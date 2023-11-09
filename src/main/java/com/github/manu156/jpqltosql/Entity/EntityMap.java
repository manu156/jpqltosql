package com.github.manu156.jpqltosql.Entity;

import com.intellij.psi.PsiClass;
import org.eclipse.persistence.jpa.jpql.parser.Expression;

public interface EntityMap {
    String getClassByAlias(String alias, Tolerance tolerance);

    String getColumnNameByClassAndField(String clazz, String field, Tolerance tolerance);

    String getCloseColumn(String field);

    String getTableByClass(String clazz, Tolerance tolerance);

    void populateClass(String clazz);

    void populateEntityDbMaps(PsiClass[] psiClasses);

    void populateAliasMap(Expression exp);

    boolean hasAlias(String alias);

    void addMainClass(String clazz);
}
