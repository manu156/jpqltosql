package com.github.manu156.jpqltosql;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiTreeUtil;
import org.eclipse.persistence.jpa.jpql.parser.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

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
            Map<String, String> aliasToFieldMap = new HashMap<>();
            populateAliasMap(aliasToFieldMap, fromClause);
            return translateSelectClause(selectClause, classToTableMap, classToFieldToColumnMap, aliasToFieldMap) + " " +
                    translateFromClause(fromClause, classToTableMap, classToFieldToColumnMap, aliasToFieldMap) + " " +
                    translateWhereClause(whereClause, classToTableMap, classToFieldToColumnMap, aliasToFieldMap);
        } else if (queryStatement instanceof UpdateStatement) {

        } else {
            // todo
        }
        return null;
    }

    private void populateAliasMap(Map<String, String> aliasMap, FromClause fromClause) {
        IdentificationVariableDeclaration ivd = (IdentificationVariableDeclaration)fromClause.getDeclaration();
        RangeVariableDeclaration rvd = (RangeVariableDeclaration) ivd.getRangeVariableDeclaration();
        aliasMap.put(rvd.getIdentificationVariable().toParsedText(), rvd.getRootObject().toParsedText());

        CollectionExpression joins = (CollectionExpression)((IdentificationVariableDeclaration) fromClause.getDeclaration()).getJoins();
        for (Expression child : joins.children()) {
            Join join = (Join) child;
            aliasMap.put(join.getIdentificationVariable().toParsedText(), join.getJoinAssociationPath().toParsedText());
        }
    }

    private String translateWhereClause(WhereClause whereClause, Map<String, String> classToTableMap,
                                        Map<String, Map<String, String>> classToFieldToColumnMap, Map<String, String> aliasToFieldMap) {
        StringBuilder res = new StringBuilder();
        Expression exp = whereClause.getConditionalExpression();
        while (true) {
            if (exp instanceof AndExpression) {
                ComparisonExpression compExp = (ComparisonExpression)((AndExpression) exp).getRightExpression();
                if (!compExp.hasLeftExpression() || !compExp.hasRightExpression())
                    throw new RuntimeException("no lr");

                String compOp = compExp.getComparisonOperator();
                String lTranslated = "";
                String rTranslated = "";
                if (compExp.getLeftExpression() instanceof StateFieldPathExpression) {
                    StateFieldPathExpression l = (StateFieldPathExpression) compExp.getLeftExpression();
                    String cl = aliasToFieldMap.get(l.getIdentificationVariable().toParsedText());
                    lTranslated = l.getIdentificationVariable() + "." + classToFieldToColumnMap.get(cl).get(l.getPath(1));
                }
                if (compExp.getRightExpression() instanceof StateFieldPathExpression) {
                    StateFieldPathExpression r = (StateFieldPathExpression) compExp.getRightExpression();
                    String cl = aliasToFieldMap.get(r.getIdentificationVariable().toParsedText());
                    rTranslated = r.getIdentificationVariable() + "." + classToFieldToColumnMap.get(cl).get(r.getPath(1));
                } else if (compExp.getRightExpression() instanceof NumericLiteral) {
                    rTranslated = ((NumericLiteral)compExp.getRightExpression()).getText();
                } else if (compExp.getRightExpression() instanceof SubExpression) {
                    rTranslated = compExp.getRightExpression().toParsedText();
                }
                res.insert(0, lTranslated + compOp + rTranslated);
                res.insert(0, " " + ((AndExpression) exp).getActualIdentifier() + " ");
                exp = ((AndExpression) exp).getLeftExpression();
            } else if (exp instanceof ComparisonExpression) {
                ComparisonExpression compExp = (ComparisonExpression) exp;
                if (!compExp.hasLeftExpression() || !compExp.hasRightExpression())
                    throw new RuntimeException("no lr");

                String lTranslated = "";
                String rTranslated = "";
                if (compExp.getLeftExpression() instanceof StateFieldPathExpression) {
                    StateFieldPathExpression l = (StateFieldPathExpression) compExp.getLeftExpression();
                    String cl = aliasToFieldMap.get(l.getIdentificationVariable().toParsedText());
                    lTranslated = l.getIdentificationVariable() + "." + classToFieldToColumnMap.get(cl).get(l.getPath(1));
                }
                if (compExp.getRightExpression() instanceof StateFieldPathExpression) {
                    StateFieldPathExpression r = (StateFieldPathExpression) compExp.getRightExpression();
                    String cl = aliasToFieldMap.get(r.getIdentificationVariable().toParsedText());
                    rTranslated = r.getIdentificationVariable() + "." + classToFieldToColumnMap.get(cl).get(r.getPath(1));
                } else if (compExp.getRightExpression() instanceof NumericLiteral) {
                    rTranslated = ((NumericLiteral)compExp.getRightExpression()).getText();
                } else if (compExp.getRightExpression() instanceof SubExpression) {
                    rTranslated = compExp.getRightExpression().toParsedText();
                }

                String compOp = compExp.getComparisonOperator();

                res.insert(0, lTranslated + compOp + rTranslated);

                exp = null;
            } else
                break;
        }
        res.insert(0, whereClause.getActualIdentifier() + " ");
        return res.toString();
    }

    private String translateFromClause(FromClause fromClause, Map<String, String> classToTableMap,
                                       Map<String, Map<String, String>> classToFieldToColumnMap, Map<String, String> aliasToFieldMap) {
        StringBuilder res = new StringBuilder(fromClause.getActualIdentifier() + " ");
        if (((IdentificationVariableDeclaration)(fromClause).getDeclaration()).hasRangeVariableDeclaration()) {
            IdentificationVariableDeclaration ivd = ((IdentificationVariableDeclaration) fromClause.getDeclaration());
            String className = ((RangeVariableDeclaration) ivd.getRangeVariableDeclaration()).getRootObject().toActualText();
            res.append(classToTableMap.get(className));
            res.append(" ");
            res.append(((RangeVariableDeclaration) ivd.getRangeVariableDeclaration()).getIdentificationVariable());
        }
        if (((IdentificationVariableDeclaration)fromClause.getDeclaration()).hasJoins()) {
            CollectionExpression joins = (CollectionExpression)((IdentificationVariableDeclaration)fromClause.getDeclaration()).getJoins();
            for (int i=0; i<joins.childrenSize(); i++) {
                Join join = (Join) joins.getChild(i);
                res.append(" ").append(join.getActualIdentifier());
                res.append(" ").append(classToTableMap.get(join.getJoinAssociationPath().toParsedText()));
                res.append(" ").append(join.getIdentificationVariable().toParsedText());
                OnClause onClause = (OnClause)join.getOnClause();
                res.append(" ").append(onClause.getActualIdentifier());
                ComparisonExpression compExp = (ComparisonExpression)onClause.getConditionalExpression();
                if (!compExp.hasLeftExpression() || !compExp.hasRightExpression())
                    throw new RuntimeException("no lr");
                StateFieldPathExpression l = (StateFieldPathExpression) compExp.getLeftExpression();
                StateFieldPathExpression r = (StateFieldPathExpression) compExp.getRightExpression();
                String compOp = compExp.getComparisonOperator();
                String cl = aliasToFieldMap.get(l.getIdentificationVariable().toParsedText());
                String lTranslated = l.getIdentificationVariable() + "." + classToFieldToColumnMap.get(cl).get(l.getPath(1));
                cl = aliasToFieldMap.get(r.getIdentificationVariable().toParsedText());
                String rTranslated = r.getIdentificationVariable() + "." + classToFieldToColumnMap.get(cl).get(r.getPath(1));
                res.append(" ").append(lTranslated).append(compOp).append(rTranslated);
            }
        }
        return res.toString();

    }

    private String translateSelectClause(SelectClause selectClause, Map<String, String> classToTableMap,
                                         Map<String, Map<String, String>> classToFieldToColumnMap,
                                         Map<String, String> aliasToFieldMap) {
        CollectionExpression constructorItems = (CollectionExpression) ((ConstructorExpression)selectClause.getSelectExpression()).getConstructorItems();
        StringBuilder selectedColumnsStr = new StringBuilder();
        for (int i=0; i<constructorItems.childrenSize(); i++) {
            StateFieldPathExpression sfpe = (StateFieldPathExpression) constructorItems.getChild(i);
            String currentClass = aliasToFieldMap.get(sfpe.getPath(0));
            String columnName = classToFieldToColumnMap.get(currentClass).get(sfpe.getPath(1));
            selectedColumnsStr.append(sfpe.getPath(0)).append(".").append(columnName);
            if (i < constructorItems.childrenSize()-1)
                selectedColumnsStr.append(", ");
        }
        return selectClause.getActualIdentifier() + " " + selectedColumnsStr;
    }

    private void populateEntityDbMaps(Map<String, Map<String, String>> classToFieldToColumnMap, Map<String, String> classToTableMap, List<PsiClass> psiClasses) {
        for (PsiClass psiClass: psiClasses) {
            PsiAnnotation psiClassAnnotation = psiClass.getAnnotation("javax.persistence.Table");
            if (null == psiClassAnnotation)
                continue;
            if (null == psiClass.getQualifiedName())
                continue;
            String className = psiClass.getQualifiedName();
            if (className.contains("."))
                className = className.substring(psiClass.getQualifiedName().lastIndexOf(".")+1);
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
