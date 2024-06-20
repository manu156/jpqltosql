package com.github.manu156.jpqltosql.Entity;

import com.github.manu156.jpqltosql.Execption.FailedTranslation;
import com.intellij.openapi.diagnostic.Logger;

import java.util.ArrayList;
import java.util.List;

public class Tolerance {
    public boolean tolerance;
    public long violations;
    public List<Class> exc;
    private static final boolean DEBUG_FLAG = true; // todo

    private Logger log = Logger.getInstance(Tolerance.class);

    public Tolerance(boolean tolerance) {
        this.tolerance = tolerance;
        violations = 0L;
        exc = new ArrayList<>();
    }

    public String getValue(String value) {
        if (!tolerance)
            throw new FailedTranslation();

        if (DEBUG_FLAG) {
            log.info("value: " + value);
        }

        violations += 1;
        return value;
    }
}
