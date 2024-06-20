package com.github.manu156.jpqltosql.Const;

import java.util.*;

public class Constants {
    public static Set<String> columnAnnotationNames = new HashSet<>(List.of(
            "javax.persistence.Column",
            "jakarta.persistence.Column"
    ));
    public static Set<String> idAnnotationNames = new HashSet<>(List.of(
            "javax.persistence.Id",
            "jakarta.persistence.Id"
    ));
    public static List<String> tableAnnotationNames = List.of(
            "javax.persistence.Table",
            "jakarta.persistence.Table"
    );
    public static List<String> entityAnnotationNames = List.of(
            "javax.persistence.Entity",
            "jakarta.persistence.Entity"
    );
    public static List<String> namedQueryAnnotations = List.of(
            "javax.persistence.NamedQuery",
            "jakarta.persistence.NamedQuery"
    );
}
