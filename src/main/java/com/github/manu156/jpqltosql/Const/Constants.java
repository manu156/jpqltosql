package com.github.manu156.jpqltosql.Const;

import java.util.*;

public class Constants {
    public static Set<String> columnAnnotationNames = new HashSet<>(List.of("javax.persistence.Column"));
    public static Set<String> idAnnotationNames = new HashSet<>(List.of("javax.persistence.Id"));
    public static String tableAnnotationName = "javax.persistence.Table";
    public static String entityAnnotationName = "javax.persistence.Entity";
    public static String namedQueryAnnotation = "javax.persistence.NamedQuery";
}
