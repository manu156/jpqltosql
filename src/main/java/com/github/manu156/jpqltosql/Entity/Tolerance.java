package com.github.manu156.jpqltosql.Entity;

import com.github.manu156.jpqltosql.Execption.FailedTranslation;

import java.util.ArrayList;
import java.util.List;

public class Tolerance {
    public boolean tolerance;
    public long violations;
    public List<Class> exc;

    public Tolerance(boolean tolerance) {
        this.tolerance = tolerance;
        violations = 0L;
        exc = new ArrayList<>();
    }

    public String getValue(String value) {
        if (!tolerance)
            throw new FailedTranslation();

        violations += 1;
        return value;
    }
}
