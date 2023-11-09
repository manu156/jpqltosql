package com.github.manu156.jpqltosql.Action;

import com.github.manu156.jpqltosql.Entity.EntityMap;
import com.github.manu156.jpqltosql.Entity.EntityMapImpl;
import com.github.manu156.jpqltosql.Entity.Tolerance;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import org.eclipse.persistence.jpa.jpql.parser.JPQLExpression;
import org.eclipse.persistence.jpa.jpql.parser.JPQLGrammar3_1;
import com.google.common.io.Resources;

import org.junit.Assert;
import org.junit.Before;

import org.junit.Test;
import org.mockito.Mock;

import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ToSqlActionTest {

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }


    @Mock
    EntityMap entityMap;


    @Test
    public void translateExpression() {
        String queryString = "SELECT b FROM Book b WHERE b.categoryId=:categoryId AND authorId=:authorId AND" +
                " authorId in :ids AND authorId in :otherIds";
//        ToSqlAction toSqlAction = new ToSqlAction();
//        Tolerance tolerance = new Tolerance(true);
//
//        JPQLExpression jpql = new JPQLExpression(queryString, new JPQLGrammar3_1(), tolerance.tolerance);
//        System.out.println("<>>>ttest " + toSqlAction.translateExpression(jpql.getQueryStatement(), entityMap, tolerance));
        setupEntityMapMock();
    }

    private void setupEntityMapMock() {
        EntityMapImpl entityMap = new EntityMapImpl(null);
//        Map<String, String> classToTableMap;
//        Map<String, Map<String, String>> classToFieldToColumnMap;
//        Map<String, String> aliasToFieldMap;
//        List<String> mainClass;
        JsonArray jsonArray = new Gson().fromJson(getResourceString("mockEntityMapBooks.json"), JsonArray.class);
        for (JsonElement jsonElement : jsonArray) {
            JsonObject entityJson = jsonElement.getAsJsonObject();
            String className = entityJson.get("name").getAsJsonObject().keySet().stream().findFirst().orElse(null);
            entityMap.classToTableMap.put(className, entityJson.get("name").getAsJsonObject().get(className).getAsString());
            entityMap.classToFieldToColumnMap.put(className, new HashMap<>());
            for (Map.Entry<String, JsonElement> elementEntry : entityJson.get("FieldToColumn").getAsJsonObject().entrySet()) {
                String field = elementEntry.getKey();
                String column = elementEntry.getValue().getAsString();

            }
        }
    }

    private String getResourceString(String fileName) {
        try {
            return Resources.toString(Resources.getResource(fileName),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}