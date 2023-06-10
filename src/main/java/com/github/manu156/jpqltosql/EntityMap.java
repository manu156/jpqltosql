package com.github.manu156.jpqltosql;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import org.eclipse.persistence.jpa.jpql.parser.*;

import java.util.*;
import java.util.stream.Collectors;

import static com.github.manu156.jpqltosql.Util.getValueByKey;

public class EntityMap {
    public Map<String, String> classToTableMap;
    public Map<String, Map<String, String>> classToFieldToColumnMap;
    public Map<String, String> aliasToFieldMap;
    private Project project;

    public EntityMap() {
        throw new RuntimeException();
    }

    public EntityMap(Project project) {
        classToTableMap = new HashMap<>();
        classToFieldToColumnMap = new HashMap<>();
        aliasToFieldMap = new HashMap<>();
        this.project = project;
    }

    public String getClassByAlias(String alias) {
        return this.aliasToFieldMap.getOrDefault(alias, alias);
    }

    public String getColumnNameByClassAndField(String clazz, String field) {
        if (!classToFieldToColumnMap.containsKey(clazz))
            populateClass(clazz);
        if (!classToFieldToColumnMap.containsKey(clazz))
            return field;
        return classToFieldToColumnMap.get(clazz).getOrDefault(field, field);
    }

    public String getTableByClass(String clazz) {
        if (!classToTableMap.containsKey(clazz))
            populateClass(clazz);
        return classToTableMap.getOrDefault(clazz, clazz);
    }

    private void populateClass(String clazz) {
        List<PsiClass> psiClasses = new ArrayList<>();
        PsiClass[] possibleClasses = PsiShortNamesCache.getInstance(project)
                .getClassesByName(clazz, GlobalSearchScope.allScope(project));

        populateEntityDbMaps(possibleClasses);
    }

    private void populateEntityDbMaps(PsiClass[] psiClasses) {
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

    public void populateAliasMap(FromClause fromClause) {
        IdentificationVariableDeclaration ivd = (IdentificationVariableDeclaration)fromClause.getDeclaration();
        RangeVariableDeclaration rvd = (RangeVariableDeclaration) ivd.getRangeVariableDeclaration();
        aliasToFieldMap.put(rvd.getIdentificationVariable().toParsedText(), rvd.getRootObject().toParsedText());

        if (fromClause.hasDeclaration()) {
            if (((IdentificationVariableDeclaration) fromClause.getDeclaration()).hasJoins()) {
                CollectionExpression joins = (CollectionExpression) ((IdentificationVariableDeclaration) fromClause.getDeclaration()).getJoins();
                for (Expression child : joins.children()) {
                    Join join = (Join) child;
                    aliasToFieldMap.put(join.getIdentificationVariable().toParsedText(), join.getJoinAssociationPath().toParsedText());
                }
            }
        }
    }
}
