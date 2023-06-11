package com.github.manu156.jpqltosql.Util;

import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiPolyadicExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class PsiProcessUtil {
    public static Map<String, String> getKeyValueMap(@NotNull PsiAnnotation psiAnnotation) {
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
    public static String getValueByKey(@NotNull PsiAnnotation psiAnnotation, @NotNull String name) {
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
