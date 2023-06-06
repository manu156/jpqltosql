package com.github.manu156.jpqltosql;

import com.intellij.lang.jvm.annotation.JvmAnnotationAttributeValue;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiJavaFileImpl;
import com.intellij.psi.impl.source.tree.java.PsiAnnotationImpl;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiTreeUtil;
import org.eclipse.persistence.jpa.jpql.parser.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ToSqlAction extends AnAction {
    @Override
    public void update(AnActionEvent e) {
        PsiFile psiFile = e.getRequiredData(CommonDataKeys.PSI_FILE);
        Caret caret = e.getRequiredData(CommonDataKeys.CARET);
        e.getPresentation().setEnabled(null != psiFile.findElementAt(caret.getOffset()));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        PsiFile psiFile = e.getRequiredData(CommonDataKeys.PSI_FILE);
        if (!"JAVA".equalsIgnoreCase(psiFile.getFileType().getName()))
            return;
        Caret caret = e.getRequiredData(CommonDataKeys.CARET);
        PsiElement psiElement = psiFile.findElementAt(caret.getOffset());
        PsiAnnotation psiAnnotation = PsiTreeUtil.getParentOfType(psiElement, PsiAnnotation.class);
        if (null == psiAnnotation)
            return;

        Map<String, String> vp = getKeyValueMap(psiAnnotation);
        String name = vp.get("name");
        String query = vp.get("query");

        JPQLExpression jpql = new JPQLExpression(query, new JPQLGrammar3_1());
        Expression queryStatement = jpql.getQueryStatement();
        Set<String> classesRequired = new HashSet<>();
        Map<String, String> fieldsRequired = new HashMap<>();

        if (queryStatement instanceof SelectStatement) {
            SelectClause selectClause = (SelectClause) ((SelectStatement) queryStatement).getSelectClause();
            FromClause fromClause = (FromClause) ((SelectStatement) queryStatement).getFromClause();
            WhereClause whereClause = (WhereClause) ((SelectStatement) queryStatement).getWhereClause();
            if (((IdentificationVariableDeclaration)((FromClause) fromClause).getDeclaration()).hasRangeVariableDeclaration())
                classesRequired.add(((RangeVariableDeclaration)((IdentificationVariableDeclaration)((FromClause) fromClause).getDeclaration()).getRangeVariableDeclaration()).getRootObject().toActualText());
            if (((IdentificationVariableDeclaration)((FromClause) fromClause).getDeclaration()).hasJoins()) {
                for (Expression exp: ((IdentificationVariableDeclaration) ((FromClause) fromClause).getDeclaration()).getJoins().children()) {
                    classesRequired.add(((Join)exp).getJoinAssociationPath().toActualText());
                }
            }

        } else if (queryStatement instanceof UpdateStatement) {

        } else {
            // todo
        }
        List<PsiClass> psiClasses = Arrays.stream(((PsiJavaFile) psiFile).getClasses()).collect(Collectors.toList());
        for (String clazz: classesRequired) {
            PsiClass[] possibleClasses = PsiShortNamesCache.getInstance(e.getRequiredData(CommonDataKeys.PROJECT))
                    .getClassesByName(clazz, GlobalSearchScope.allScope(e.getRequiredData(CommonDataKeys.PROJECT)));
            psiClasses.addAll(List.of(possibleClasses));
        }
        Map<String, Map<String, String>> classToFieldToColumnMap = new HashMap<>();
        Map<String, String> classToTableMap = new HashMap<>();
        populateEntityDbMaps(classToFieldToColumnMap, classToTableMap, psiClasses);

        String sql = translateQuery(classToTableMap, classToFieldToColumnMap, jpql);
        Messages.showInfoMessage(sql, name);

    }

    private String translateQuery(Map<String, String> classToTableMap,
                                  Map<String, Map<String, String>> classToFieldToColumnMap, JPQLExpression jpql) {
        Expression queryStatement = jpql.getQueryStatement();

        if (queryStatement instanceof SelectStatement) {

            SelectClause selectClause = (SelectClause) ((SelectStatement) queryStatement).getSelectClause();
            FromClause fromClause = (FromClause) ((SelectStatement) queryStatement).getFromClause();
            WhereClause whereClause = (WhereClause) ((SelectStatement) queryStatement).getWhereClause();
            return translateSelectClause(selectClause, classToTableMap, classToFieldToColumnMap) + " " +
                    translateFromClause(fromClause, classToTableMap, classToFieldToColumnMap) + " " +
                    translateWhereClause(whereClause, classToTableMap, classToFieldToColumnMap);
        } else if (queryStatement instanceof UpdateStatement) {

        } else {
            // todo
        }
        return null;
    }

    private String translateWhereClause(WhereClause whereClause, Map<String, String> classToTableMap, Map<String, Map<String, String>> classToFieldToColumnMap) {
        return whereClause.toParsedText();
    }

    private String translateFromClause(FromClause fromClause, Map<String, String> classToTableMap, Map<String, Map<String, String>> classToFieldToColumnMap) {
//        return fromClause.toParsedText();
        String init = fromClause.toParsedText();
        for (Map.Entry<String, String> kv : classToTableMap.entrySet()) {
            init = init.replace(kv.getKey(), kv.getValue());
            init = init.replace(kv.getKey().substring(kv.getKey().lastIndexOf(".")+1), kv.getValue());
        }
        return init;
    }

    private String translateSelectClause(SelectClause selectClause, Map<String, String> classToTableMap, Map<String, Map<String, String>> classToFieldToColumnMap) {
        return selectClause.toParsedText();
    }

    private void populateEntityDbMaps(Map<String, Map<String, String>> classToFieldToColumnMap, Map<String, String> classToTableMap, List<PsiClass> psiClasses) {
        for (PsiClass psiClass: psiClasses) {
            PsiAnnotation psiClassAnnotation = psiClass.getAnnotation("javax.persistence.Table");
            if (null == psiClassAnnotation)
                continue;
            String className = psiClass.getQualifiedName();
            String tableName = getValueByKey(psiClassAnnotation, "name");
            if (null == tableName)
                continue;
            classToTableMap.put(className, tableName);
            Map<String, String> fieldToColumnMap = new HashMap<>();
            classToFieldToColumnMap.put(className, fieldToColumnMap);
            PsiField[] psiFields = psiClass.getAllFields();
            for (PsiField psiField : psiFields) {
                String varName = psiField.getName();
                PsiAnnotation psiColumnAnnotation = psiField.getAnnotation("javax.persistence.Column");
                if (null == psiColumnAnnotation)
                    continue;
                String colName = getValueByKey(psiColumnAnnotation, "name");
                if (null == colName)
                    continue;
                fieldToColumnMap.put(varName, colName);
            }
        }
    }

    private Map<String, String> getKeyValueMap(@NotNull PsiAnnotation psiAnnotation) {
        PsiNameValuePair[] psiNameValuePairs = psiAnnotation.getParameterList().getAttributes();
        Map<String, String> result = new HashMap<>();
        for (PsiNameValuePair pair: psiNameValuePairs) {
            if (null != pair.getLiteralValue())
                result.put(pair.getName(), pair.getLiteralValue());
            else if (null != pair.getValue()) {
                String value;
                if (pair.getValue() instanceof PsiPolyadicExpression)
                    value = Strings.join(Arrays.stream(((PsiPolyadicExpression)pair.getValue()).getOperands())
                            .map(t -> t.getText().length() > 2 ? t.getText().substring(1, t.getText().length()-1) : "")
                            .collect(Collectors.toList()), "");
                else {
                    String text = pair.getValue().getText();
                    value = text.length() > 2 ? text.substring(1, text.length() - 1) : "";
                }
                result.put(pair.getName(), value);
            }
        }
        return result;
    }

    @Nullable
    private String getValueByKey(@NotNull PsiAnnotation psiAnnotation, @NotNull String name) {
        PsiNameValuePair[] psiNameValuePairs = psiAnnotation.getParameterList().getAttributes();
        for (PsiNameValuePair pair: psiNameValuePairs) {
            if (null != pair.getLiteralValue() && name.equals(pair.getName()))
                return pair.getLiteralValue();
            else if (null != pair.getValue() && name.equals(pair.getName())) {
                String value;
                if (pair.getValue() instanceof PsiPolyadicExpression)
                    value = Strings.join(Arrays.stream(((PsiPolyadicExpression)pair.getValue()).getOperands())
                            .map(t -> t.getText().length() > 2 ? t.getText().substring(1, t.getText().length()-1) : "")
                            .collect(Collectors.toList()), "");
                else {
                    String text = pair.getValue().getText();
                    value = text.length() > 2 ? text.substring(1, text.length() - 1) : "";
                }
                return value;
            }
        }
        return null;
    }
}
