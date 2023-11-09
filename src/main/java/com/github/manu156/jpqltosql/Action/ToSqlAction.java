package com.github.manu156.jpqltosql.Action;

import com.github.manu156.jpqltosql.Entity.EntityMap;
import com.github.manu156.jpqltosql.Entity.EntityMapImpl;
import com.github.manu156.jpqltosql.Entity.Tolerance;
import com.github.manu156.jpqltosql.Execption.FailedTranslation;
import com.github.manu156.jpqltosql.Execption.QueryNotFound;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.apache.commons.lang.StringUtils;
import org.eclipse.persistence.jpa.jpql.parser.*;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.util.*;
import java.util.stream.Collectors;


import static com.github.manu156.jpqltosql.Const.Constants.namedQueryAnnotation;
import static com.github.manu156.jpqltosql.Util.CStringUtil.strip;
import static com.github.manu156.jpqltosql.Util.PsiProcessUtil.getKeyValueMap;
import static com.github.manu156.jpqltosql.Util.PsiProcessUtil.getValueByKey;


public class ToSqlAction extends AnAction {
    @Override
    public void update(AnActionEvent e) {
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        Caret caret = e.getData(CommonDataKeys.CARET);
        if (null != psiFile && null != caret) {
            PsiElement psiElement = psiFile.findElementAt(caret.getOffset());
            PsiAnnotation psiAnnotation = PsiTreeUtil.getParentOfType(psiElement, PsiAnnotation.class);
            if (null != psiAnnotation && namedQueryAnnotation.equals(psiAnnotation.getQualifiedName())
                    && null != getValueByKey(psiAnnotation, "query")) {
                e.getPresentation().setEnabled(true);
                return;
            }

            PsiPolyadicExpression psiDeclaration = PsiTreeUtil.getParentOfType(psiElement, PsiPolyadicExpression.class);
            if (null != psiDeclaration) {
                e.getPresentation().setEnabled(
                        Arrays.stream(psiDeclaration.getChildren()).anyMatch(PsiLiteralExpression.class::isInstance)
                );
                return;
            }

            PsiLiteralExpression psiLiteralExpression = PsiTreeUtil.getParentOfType(psiElement, PsiLiteralExpression.class);
            if (null != psiLiteralExpression) {
                e.getPresentation().setEnabled(true);
                return;
            }
        }

        e.getPresentation().setEnabled(false);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Tolerance tolerance = new Tolerance(true);
        String name = "";
        String querySource = "Translated String Under Cursor";
        try {
            PsiFile psiFile = e.getRequiredData(CommonDataKeys.PSI_FILE);
            if (!"JAVA".equalsIgnoreCase(psiFile.getFileType().getName()))
                return;
            Caret caret = e.getRequiredData(CommonDataKeys.CARET);
            PsiElement psiElement = psiFile.findElementAt(caret.getOffset());
            Map<String, String> vp = getQueryFromPsiElement(psiElement);
//            if (null == vp) {
//                return;
//            }
            String query = vp.get("query");
            name = vp.getOrDefault("name",
                    vp.get("query").substring(0, Math.min(65, vp.get("query").length())) + ".."
            );
            querySource = vp.get("querySource");

//            JPQLExpression jpql = new JPQLExpression(query, new JPQLGrammar3_1(), tolerance.tolerance); // todo
            JPQLExpression jpql = new JPQLExpression(query, new EclipseLinkJPQLGrammar4_0(), tolerance.tolerance);
            if (StringUtils.isEmpty(query) || StringUtils.isEmpty(jpql.getQueryStatement().toParsedText()))
                throw new QueryNotFound();
            String sql = translateExpression(jpql.getQueryStatement(), new EntityMapImpl(e.getRequiredData(CommonDataKeys.PROJECT)), tolerance);
            CopyPasteManager.getInstance().setContents(new StringSelection(sql));
            if (tolerance.violations > 0) {
                sendNotification(e, "Copied partially translated SQL to clipboard ", "Translated: " + name, NotificationType.WARNING);
            } else {
                sendNotification(e, "Copied to clipboard", "Translated: " + name, NotificationType.INFORMATION);
            }
        } catch (QueryNotFound ex) {
            sendNotification(e, "No query found!", NotificationType.ERROR);
        } catch (FailedTranslation ex) {
            sendNotification(e, "Failed to translate " + (!Objects.equals(name, "") ? ":" + name : ""),
                            NotificationType.ERROR);
        } catch (Exception ex) {
            sendNotification(e, "Failed to translate! Unknown Error " + (!Objects.equals(name, "") ? ":" + name : ""),
                            NotificationType.ERROR);
        }
    }

    private static void sendNotification(@NotNull AnActionEvent e, String title, String desc, NotificationType notificationType) {
        if (StringUtils.isEmpty(title)) {
            sendNotification(e, desc, notificationType);
            return;
        }
        NotificationGroupManager.getInstance()
                .getNotificationGroup("JPQL2SQL")
                .createNotification(title, desc, notificationType)
                .notify(e.getRequiredData(CommonDataKeys.PROJECT));
    }

    private static void sendNotification(@NotNull AnActionEvent e, String desc, NotificationType notificationType) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("JPQL2SQL")
                .createNotification(desc, notificationType)
                .notify(e.getRequiredData(CommonDataKeys.PROJECT));
    }

    private Map<String, String> getQueryFromPsiElement(PsiElement psiElement) {
        PsiAnnotation psiAnnotation = PsiTreeUtil.getParentOfType(psiElement, PsiAnnotation.class);
        Map<String, String> vp = null;
        if (null != psiAnnotation) {
            vp = getKeyValueMap(psiAnnotation);
//            if (!vp.containsKey("name"))
//                vp.put("name", "JPQL2SQL");
            if (vp.containsKey("query"))
                return vp;
        }

        // do logic for getting normal string
//        PsiDeclarationStatement psiDeclaration = PsiTreeUtil.getParentOfType(psiElement, PsiDeclarationStatement.class);
        PsiPolyadicExpression psiDeclaration = PsiTreeUtil.getParentOfType(psiElement, PsiPolyadicExpression.class);
        if (null != psiDeclaration) {
            String value = Strings.join(Arrays.stream((psiDeclaration.getChildren())).filter(t -> t instanceof PsiLiteralExpression)
                        .map(t -> strip(strip(t.getText(), '"'), "\\n"))
                        .collect(Collectors.toList()), "");
            vp = new HashMap<>();
//            if (!vp.containsKey("name"))
//                vp.put("name", "JPQL2SQL");
            vp.put("query", value);
            return vp;
        }

        PsiLiteralExpression psiLiteralExpression = PsiTreeUtil.getParentOfType(psiElement, PsiLiteralExpression.class);
        if (null != psiLiteralExpression) {
            String text = Objects.requireNonNull(psiLiteralExpression).getText();
            String value = strip(text, '"');
            vp = new HashMap<>();
//            if (!vp.containsKey("name"))
//                vp.put("name", "JPQL2SQL");
            vp.put("query", value);
            return vp;
        }

        throw new QueryNotFound();
    }

    public String translateExpression(Expression exp, EntityMap entityMap, Tolerance tolerance) {
        if (exp instanceof NumericLiteral numericLiteral) {
            return numericLiteral.getText();
        } else if (exp instanceof StateFieldPathExpression pathExp) {
            String cl = entityMap.getClassByAlias(pathExp.getIdentificationVariable().toParsedText(), tolerance);
            return pathExp.getIdentificationVariable() + "." + entityMap.getColumnNameByClassAndField(cl, pathExp.getPath(1), tolerance);
        } else if (exp instanceof SubExpression subExpression) {
            return "(" + translateExpression(subExpression.getExpression(), entityMap, tolerance) + ")";
        } else if (exp instanceof InputParameter) {
            return exp.toParsedText();
        } else if (exp instanceof IdentificationVariable) {
            if (exp.getParent() instanceof SelectClause) {
                if (entityMap.hasAlias(exp.toParsedText()))
                    return exp.toParsedText() + ".*";
            } else if (exp.getParent() instanceof CompoundExpression) {
                String clc = entityMap.getCloseColumn(exp.toParsedText());
                if (null != clc)
                    return clc;
            }
            return exp.toParsedText();
        } else if (exp instanceof ConstructorExpression consExp) {
            return translateExpression(consExp.getConstructorItems(), entityMap, tolerance);
        } else if (exp instanceof CollectionExpression constructorItems) {
            StringBuilder selectedColumnsStr = new StringBuilder();
            for (int i=0; i<constructorItems.childrenSize(); i++) {
                selectedColumnsStr.append(translateExpression(constructorItems.getChild(i), entityMap, tolerance));
                if (i < constructorItems.childrenSize()-1 && constructorItems.hasComma(i))
                    selectedColumnsStr.append(",");
                if (i < constructorItems.childrenSize()-1 && constructorItems.hasSpace(i))
                    selectedColumnsStr.append(" ");
            }
            return selectedColumnsStr.toString();
        } else if (exp instanceof FromClause fromClause) {
            StringBuilder res = new StringBuilder(fromClause.getActualIdentifier() + " ");
            if (((IdentificationVariableDeclaration)(fromClause).getDeclaration()).hasRangeVariableDeclaration()) {
                IdentificationVariableDeclaration ivd = ((IdentificationVariableDeclaration) fromClause.getDeclaration());
                String className = ((RangeVariableDeclaration) ivd.getRangeVariableDeclaration()).getRootObject().toActualText();
                entityMap.addMainClass(className);
                res.append(entityMap.getTableByClass(className, tolerance));
                res.append(" ");
                res.append(((RangeVariableDeclaration) ivd.getRangeVariableDeclaration()).getIdentificationVariable());
            }
            if (((IdentificationVariableDeclaration)fromClause.getDeclaration()).hasJoins()) {
                Expression joins = ((IdentificationVariableDeclaration)fromClause.getDeclaration()).getJoins();
                res.append(translateExpression(joins, entityMap, tolerance));
            }
            return res.toString();
        } else if (exp instanceof ComparisonExpression compExp) {
            if (!compExp.hasLeftExpression() || !compExp.hasRightExpression())
                throw new RuntimeException("no lr");
            String compOp = compExp.getComparisonOperator();
            String lTranslated = translateExpression(compExp.getLeftExpression(), entityMap, tolerance);
            String rTranslated = translateExpression(compExp.getRightExpression(), entityMap, tolerance);
            return lTranslated + compOp + rTranslated;
        } else if (exp instanceof Join join) {
            if (join.hasOnClause()) {
                OnClause onClause = (OnClause)join.getOnClause();
                String cls = entityMap.getTableByClass(join.getJoinAssociationPath().toParsedText(), tolerance);
                entityMap.addMainClass(cls);
                return " " + join.getActualIdentifier() +
                        " " + cls +
                        " " + join.getIdentificationVariable().toParsedText() +
                        " " + onClause.getActualIdentifier() +
                        " " + translateExpression(onClause.getConditionalExpression(), entityMap, tolerance);
            }
            // todo: from Address address left outer join fetch address.student where address.id=:id
            String als = join.getJoinAssociationPath().toParsedText();
            String fld = "";
            if (als.contains(".")) {
                String[] splt = StringUtils.split(join.getJoinAssociationPath().toParsedText(), ".");
                als = splt[0];
                fld = splt[1];
            }
            String cls = entityMap.getClassByAlias(als, tolerance);
//            String tbl = entityMap.getTableByClass(cls, tolerance);
            entityMap.addMainClass(cls);
            return " " + join.getActualIdentifier() +
                    " " + cls +
                    "." + fld;
        } else if (exp instanceof WhereClause whereClause) {
            StringBuilder res = new StringBuilder();
            Expression t = whereClause.getConditionalExpression();
            while (true) {
                if (t instanceof AndExpression) {
                    res.insert(0, translateExpression(((AndExpression) t).getRightExpression(), entityMap, tolerance));
                    res.insert(0, " " + ((AndExpression) t).getActualIdentifier() + " ");
                    t = ((AndExpression) t).getLeftExpression();
                } else {
                    res.insert(0, translateExpression(t, entityMap, tolerance));
                    break;
                }
            }
            res.insert(0, ((WhereClause)exp).getActualIdentifier() + " ");
            return res.toString();
        } else if (exp instanceof AndExpression andExpression) {
            return translateExpression(andExpression.getLeftExpression(), entityMap, tolerance) + " " +
                    andExpression.getActualIdentifier() + " " +
                    translateExpression(andExpression.getRightExpression(), entityMap, tolerance);
        } else if (exp instanceof SelectStatement s) {
            if (s.hasFromClause())
                entityMap.populateAliasMap((FromClause) s.getFromClause());
            String selectAndFrom = (s.hasSelectClause() ? translateExpression(s.getSelectClause(), entityMap, tolerance) + " " : "") +
                    (s.hasFromClause() ? translateExpression(s.getFromClause(), entityMap, tolerance) + " " : "");

            return selectAndFrom +
                    (s.hasWhereClause() ? translateExpression(s.getWhereClause(), entityMap, tolerance) : "") +
                    (s.hasGroupByClause() ? " " + translateExpression(s.getGroupByClause(), entityMap, tolerance) : "") +
                    (s.hasHavingClause() ? " " + translateExpression(s.getHavingClause(), entityMap, tolerance): "") +
                    (s.hasOrderByClause() ? " " + translateExpression(s.getOrderByClause(), entityMap, tolerance) : "");
        } else if (exp instanceof SelectClause selectClause) {
            return selectClause.getActualIdentifier() + " " +
                    translateExpression(selectClause.getSelectExpression(), entityMap, tolerance);
        } else if (exp instanceof StringLiteral) {
            return exp.toParsedText();
        } else if (exp instanceof NullComparisonExpression nullCompExp) {
            return translateExpression((nullCompExp).getExpression(), entityMap, tolerance) + " " +
                    nullCompExp.getActualIsIdentifier() + " " +
                    (nullCompExp.hasNot() ? nullCompExp.getActualNotIdentifier() + " " : "") +
                    nullCompExp.getActualNullIdentifier();
        } else if (exp instanceof BetweenExpression btwExp) {
            return translateExpression(btwExp.getExpression(), entityMap, tolerance) + " " +
                    (btwExp.hasNot() ? btwExp.getActualNotIdentifier() + " " : "") +
                    btwExp.getActualBetweenIdentifier() + " " +
                    translateExpression(btwExp.getLowerBoundExpression(), entityMap, tolerance) + " " +
                    translateExpression(btwExp.getUpperBoundExpression(), entityMap, tolerance);
        } else if (exp instanceof ResultVariable resExp) {
            return translateExpression(resExp.getSelectExpression(), entityMap, tolerance) + " " +
                    resExp.getActualAsIdentifier() + " " +
                    resExp.getResultVariable().toParsedText();
        } else if (exp instanceof AggregateFunction aggregateFunction) {
            return aggregateFunction.getActualIdentifier() + "(" +
                    translateExpression(aggregateFunction.getExpression(), entityMap, tolerance) + ")";
        } else if (exp instanceof GroupByClause groupByClause) {
            return groupByClause.getActualIdentifier() + " " +
                    translateExpression(groupByClause.getGroupByItems(), entityMap, tolerance);
        } else if (exp instanceof OrExpression orExpression) {
            return translateExpression(orExpression.getLeftExpression(), entityMap, tolerance) + " " +
                    orExpression.getActualIdentifier() + " " +
                    translateExpression(orExpression.getRightExpression(), entityMap, tolerance);
        } else if (exp instanceof HavingClause havingClause) {
            return havingClause.getActualIdentifier() + " " +
                    translateExpression(havingClause.getConditionalExpression(), entityMap, tolerance);
        } else if (exp instanceof InExpression inExp) {
            return translateExpression(inExp.getExpression(), entityMap, tolerance) + " " +
                    (inExp.hasNot() ? inExp.getActualNotIdentifier() + " " : "") +
                    (inExp.hasInItems() ? inExp.getActualInIdentifier() + " " : "") +
                    (inExp.hasLeftParenthesis() ? "(" : "") +
                    translateExpression(inExp.getInItems(), entityMap, tolerance) +
                    (inExp.hasRightParenthesis() ? ")" : "");
        } else if (exp instanceof ArithmeticExpression arithmeticExpression) {
            return translateExpression(arithmeticExpression.getLeftExpression(), entityMap, tolerance) + " " +
                    arithmeticExpression.getActualIdentifier() + " " +
                    translateExpression(arithmeticExpression.getRightExpression(), entityMap, tolerance);
        } else if (exp instanceof CaseExpression caseExpression) {
            StringBuilder res = new StringBuilder();
            res.append(caseExpression.getActualCaseIdentifier());
            if (caseExpression.hasWhenClauses()) {
                for (Expression child : caseExpression.getWhenClauses().children()) {
                    res.append(" ").append(translateExpression(child, entityMap, tolerance));
                }
            }
            if (caseExpression.hasElseExpression()) {
                res.append(" ").append(caseExpression.getActualElseIdentifier()).append(" ")
                        .append(translateExpression(caseExpression.getElseExpression(), entityMap, tolerance));
            }
            if (caseExpression.hasEnd()) {
                res.append(" ").append(caseExpression.getActualEndIdentifier());
            }
            return res.toString();
        } else if (exp instanceof WhenClause whenClause) {
            return (whenClause.hasWhenExpression() ?
                            whenClause.getActualWhenIdentifier() + " " +
                            translateExpression(whenClause.getWhenExpression(), entityMap, tolerance) : "") +
                    (whenClause.hasThenExpression() ?
                            " " + whenClause.getActualThenIdentifier() + " " +
                            translateExpression(whenClause.getThenExpression(), entityMap, tolerance): "");
        } else if (exp instanceof UpdateStatement updateStatement) {
            return translateExpression(updateStatement.getUpdateClause(), entityMap, tolerance)
                    + (updateStatement.hasWhereClause() ? " "
                            + translateExpression(updateStatement.getWhereClause(), entityMap, tolerance) : "");
        } else if (exp instanceof UpdateClause updateClause) {
            if (updateClause.hasRangeVariableDeclaration())
                entityMap.populateAliasMap(updateClause.getRangeVariableDeclaration());
            return updateClause.getActualUpdateIdentifier() +
                    (updateClause.hasRangeVariableDeclaration() ? " " + translateExpression(updateClause.getRangeVariableDeclaration(), entityMap, tolerance) : "") +
                    (updateClause.hasSet() ? " " + updateClause.getActualSetIdentifier() : "") +
                    (updateClause.hasUpdateItems() ? " " + translateExpression(updateClause.getUpdateItems(), entityMap, tolerance) : "");
        } else if (exp instanceof UpdateItem updateItem) {
            return translateExpression(updateItem.getStateFieldPathExpression(), entityMap, tolerance) +
                    "=" + translateExpression(updateItem.getNewValue(), entityMap, tolerance);
        } else if (exp instanceof RangeVariableDeclaration rvd) {
            String className = rvd.getRootObject().toActualText();
            return entityMap.getTableByClass(className, tolerance) + " " + rvd.getIdentificationVariable();
        } else if (exp instanceof BadExpression badExpression) {
            if (tolerance.tolerance) {
                tolerance.violations += 1;
                return translateExpression(badExpression.getExpression(), entityMap, tolerance);
            }
            throw new FailedTranslation();
        } else {
            if (tolerance.tolerance) {
                tolerance.violations += 1;
                return "";
            }
            throw new FailedTranslation();
        }
    }
}
