package com.github.manu156.jpqltosql.Entity;


import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import org.apache.commons.lang.StringUtils;
import org.eclipse.persistence.jpa.jpql.parser.*;

import java.util.*;


import static com.github.manu156.jpqltosql.Const.Constants.*;
import static com.github.manu156.jpqltosql.Const.Constants.entityAnnotationNames;
import static com.github.manu156.jpqltosql.Util.PsiProcessUtil.getValueByKey;

public class EntityMapImpl implements EntityMap {
    public Map<String, String> classToTableMap;
    public Map<String, Map<String, String>> classToFieldToColumnMap;
    public Map<String, String> aliasToFieldMap;
    private final Project project;
    public List<String> mainClass;

    public EntityMapImpl(Project project) {
        classToTableMap = new HashMap<>();
        classToFieldToColumnMap = new HashMap<>();
        aliasToFieldMap = new HashMap<>();
        this.project = project;
        mainClass = new ArrayList<>();
    }

    @Override
    public String getClassByAlias(String alias, Tolerance tolerance) {
        if (aliasToFieldMap.containsKey(alias))
            return this.aliasToFieldMap.get(alias);
        return tolerance.getValue(alias);
    }

    @Override
    public String getColumnNameByClassAndField(String clazz, String field, Tolerance tolerance) {
        if (!classToFieldToColumnMap.containsKey(clazz))
            populateClass(clazz);
        if (!classToFieldToColumnMap.containsKey(clazz))
            return tolerance.getValue(field);
        if (!classToFieldToColumnMap.get(clazz).containsKey(field))
            return tolerance.getValue(field);
        return classToFieldToColumnMap.get(clazz).get(field);
    }

    @Override
    public String getCloseColumn(String field) {
        for (String aClass : mainClass) {
            if (classToFieldToColumnMap.containsKey(aClass)
                    && classToFieldToColumnMap.get(aClass).containsKey(field)) {
                return classToFieldToColumnMap.get(aClass).get(field);
            }
        }
        return null;
    }

    @Override
    public String getTableByClass(String clazz, Tolerance tolerance) {
        if (!classToTableMap.containsKey(clazz))
            populateClass(clazz);
        if (!classToTableMap.containsKey(clazz))
            return tolerance.getValue(clazz);
        return classToTableMap.get(clazz);
    }

    @Override
    public void populateClass(String clazz) {
        PsiClass[] possibleClasses = PsiShortNamesCache.getInstance(project)
                .getClassesByName(clazz, GlobalSearchScope.allScope(project));

        populateEntityDbMaps(possibleClasses);
    }

    @Override
    public void populateEntityDbMaps(PsiClass[] psiClasses) {
        for (PsiClass psiClass: psiClasses) {
            // todo: implement proper logic
            PsiAnnotation psiEntityAnnotation = null;
            for (String entityAnnotationName : entityAnnotationNames) {
                psiEntityAnnotation = psiClass.getAnnotation(entityAnnotationName);
                if (psiEntityAnnotation != null) {
                    break;
                }
            }
            if (psiEntityAnnotation == null) {
                for (String tableAnnotationName : tableAnnotationNames) {
                    psiEntityAnnotation = psiClass.getAnnotation(tableAnnotationName);
                    if (psiEntityAnnotation != null) {
                        break;
                    }
                }
            }

            if (null == psiEntityAnnotation)
                continue;
            String className = psiClass.getQualifiedName();
            if (null == className)
                continue;
            if (className.contains("."))
                className = className.substring(psiClass.getQualifiedName().lastIndexOf(".")+1);

            // todo: implement proper logic
            PsiAnnotation psiClassAnnotation = null;
            for (String tableAnnotationName : tableAnnotationNames) {
                psiClassAnnotation = psiClass.getAnnotation(tableAnnotationName);
                if (psiClassAnnotation != null) {
                    break;
                }
            }

            String tableName;
            if (null == psiClassAnnotation || null == psiClass.getQualifiedName())
                tableName = className;
            else {
                tableName = getValueByKey(psiClassAnnotation, "name");
                if (null == tableName)
                    continue;
            }
            classToTableMap.put(className, tableName);
            Map<String, String> fieldToColumnMap = new HashMap<>();
            classToFieldToColumnMap.put(className, fieldToColumnMap);
            PsiField[] psiFields = psiClass.getAllFields();
            for (PsiField psiField : psiFields) {
                String varName = psiField.getName();
                for (PsiAnnotation psiColumnAnnotation: psiField.getAnnotations()) {
                    String colName = null;
                    if (idAnnotationNames.contains(psiColumnAnnotation.getQualifiedName()))
                        colName = "id";
                    if (columnAnnotationNames.contains(psiColumnAnnotation.getQualifiedName()))
                        colName = getValueByKey(psiColumnAnnotation, "name");

                    if (null == colName)
                        continue;
                    fieldToColumnMap.put(varName, colName);
                }
            }
        }
    }

    @Override
    public void populateAliasMap(Expression exp) {
        if (exp instanceof FromClause fromClause) {
            IdentificationVariableDeclaration ivd = (IdentificationVariableDeclaration) fromClause.getDeclaration();
            RangeVariableDeclaration rvd = (RangeVariableDeclaration) ivd.getRangeVariableDeclaration();
            aliasToFieldMap.put(rvd.getIdentificationVariable().toParsedText(), rvd.getRootObject().toParsedText());
            // todo fix for join fetch. see: https://docs.jboss.org/hibernate/orm/4.3/manual/en-US/html/ch20.html#performance-fetching
            if (fromClause.hasDeclaration()) {
                if (ivd.hasJoins()) {
                    Expression joins = ivd.getJoins();
                    if (joins instanceof CollectionExpression) {
                        for (Expression child : joins.children()) {
                            Join join = (Join) child;
                            if (null == join.getIdentificationVariable() || StringUtils.isEmpty(join.getIdentificationVariable().toParsedText()))
                                continue;
                            aliasToFieldMap.put(join.getIdentificationVariable().toParsedText(), join.getJoinAssociationPath().toParsedText());
                        }
                    } else if (joins instanceof Join join) {
                        if (null != join.getIdentificationVariable() && !StringUtils.isEmpty(join.getIdentificationVariable().toParsedText()))
                            aliasToFieldMap.put(join.getIdentificationVariable().toParsedText(), join.getJoinAssociationPath().toParsedText());
                    }
                }
            }
        } else if (exp instanceof RangeVariableDeclaration rvd) {
            aliasToFieldMap.put(rvd.getIdentificationVariable().toParsedText(), rvd.getRootObject().toParsedText());
        }
    }

    @Override
    public boolean hasAlias(String alias) {
        return aliasToFieldMap.containsKey(alias);
    }

    @Override
    public void addMainClass(String clazz) {
        mainClass.add(clazz);
    }
}
