package com.github.manu156.jpqltosql.Action;

import com.github.manu156.jpqltosql.Entity.EntityMap;
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
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.eclipse.persistence.jpa.jpql.parser.*;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.util.*;

import static com.github.manu156.jpqltosql.Const.Constants.namedQueryAnnotation;
import static com.github.manu156.jpqltosql.Util.PsiProcessUtil.getKeyValueMap;
import static com.github.manu156.jpqltosql.Util.PsiProcessUtil.getValueByKey;

public class ToSqlAction extends AnAction {
    @Override
    public void update(AnActionEvent e) {
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        Caret caret = e.getData(CommonDataKeys.CARET);
        if (null == psiFile || null == caret) {
            e.getPresentation().setEnabled(false);
            return;
        }
        PsiElement psiElement = psiFile.findElementAt(caret.getOffset());
        PsiAnnotation psiAnnotation = PsiTreeUtil.getParentOfType(psiElement, PsiAnnotation.class);
        e.getPresentation().setEnabled(null != psiAnnotation
                && namedQueryAnnotation.equals(psiAnnotation.getQualifiedName())
                && null != getValueByKey(psiAnnotation, "query"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Tolerance tolerance = new Tolerance(true);
        String name = "";
        try {
            PsiFile psiFile = e.getRequiredData(CommonDataKeys.PSI_FILE);
            if (!"JAVA".equalsIgnoreCase(psiFile.getFileType().getName()))
                return;
            Caret caret = e.getRequiredData(CommonDataKeys.CARET);
            PsiElement psiElement = psiFile.findElementAt(caret.getOffset());
            PsiAnnotation psiAnnotation = PsiTreeUtil.getParentOfType(psiElement, PsiAnnotation.class);
            if (null == psiAnnotation)
                return;

            Map<String, String> vp = getKeyValueMap(psiAnnotation);
            name = vp.getOrDefault("name", "JPQL2SQL");
            String query = vp.get("query");
            if (!vp.containsKey("query"))
                throw new QueryNotFound();

            JPQLExpression jpql = new JPQLExpression(query, new JPQLGrammar3_1(), tolerance.tolerance);
            String sql = translateExpression(jpql.getQueryStatement(), new EntityMap(e.getRequiredData(CommonDataKeys.PROJECT)), tolerance);
            CopyPasteManager.getInstance().setContents(new StringSelection(sql));
            if (tolerance.violations > 0) {
                NotificationGroupManager.getInstance()
                        .getNotificationGroup("JPQL2SQL")
                        .createNotification("Copied partially translated SQL: " + name, NotificationType.WARNING)
                        .notify(e.getRequiredData(CommonDataKeys.PROJECT));
            } else {
                NotificationGroupManager.getInstance()
                        .getNotificationGroup("JPQL2SQL")
                        .createNotification("Copied to SQL: " + name, NotificationType.INFORMATION)
                        .notify(e.getRequiredData(CommonDataKeys.PROJECT));
            }
        } catch (QueryNotFound ex) {
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("JPQL2SQL")
                    .createNotification("No query found", NotificationType.ERROR)
                    .notify(e.getRequiredData(CommonDataKeys.PROJECT));
        } catch (FailedTranslation ex) {
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("JPQL2SQL")
                    .createNotification("Failed to translate to SQL" +
                                    (!Objects.equals(name, "") ? ":" + name : ""),
                            NotificationType.ERROR)
                    .notify(e.getRequiredData(CommonDataKeys.PROJECT));
        } catch (Exception ex) {
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("JPQL2SQL")
                    .createNotification("Failed to translate to SQL: Unknown Error" +
                            (!Objects.equals(name, "") ? ":" + name : ""),
                            NotificationType.ERROR)
                    .notify(e.getRequiredData(CommonDataKeys.PROJECT));
        }
    }

    private String translateExpression(Expression exp, EntityMap entityMap, Tolerance tolerance) {
        if (exp instanceof NumericLiteral) {
            return ((NumericLiteral)exp).getText();
        } else if (exp instanceof StateFieldPathExpression pathExp) {
            String cl = entityMap.getClassByAlias(pathExp.getIdentificationVariable().toParsedText(), tolerance);
            return pathExp.getIdentificationVariable() + "." + entityMap.getColumnNameByClassAndField(cl, pathExp.getPath(1), tolerance);
        } else if (exp instanceof SubExpression) {
            return "(" + translateExpression(((SubExpression) exp).getExpression(), entityMap, tolerance) + ")";
        } else if (exp instanceof InputParameter) {
            return exp.toParsedText();
        } else if (exp instanceof IdentificationVariable) {
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
            return " " + lTranslated + compOp + rTranslated;
        } else if (exp instanceof Join join) {
            OnClause onClause = (OnClause)join.getOnClause();

            return " " + join.getActualIdentifier() +
            " " + entityMap.getTableByClass(join.getJoinAssociationPath().toParsedText(), tolerance) +
            " " + join.getIdentificationVariable().toParsedText() +
            " " + onClause.getActualIdentifier() +
            translateExpression(onClause.getConditionalExpression(), entityMap, tolerance);
        } else if (exp instanceof WhereClause) {
            StringBuilder res = new StringBuilder();
            Expression t = ((WhereClause) exp).getConditionalExpression();
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

            return (s.hasSelectClause() ? translateExpression(s.getSelectClause(), entityMap, tolerance) + " " : "") +
                    (s.hasFromClause() ? translateExpression(s.getFromClause(), entityMap, tolerance) + " " : "") +
                    (s.hasWhereClause() ? translateExpression(s.getWhereClause(), entityMap, tolerance) + " " : "") +
                    (s.hasGroupByClause() ? translateExpression(s.getGroupByClause(), entityMap, tolerance) + " " : "") +
                    (s.hasHavingClause() ? translateExpression(s.getHavingClause(), entityMap, tolerance) + " " : "") +
                    (s.hasOrderByClause() ? translateExpression(s.getOrderByClause(), entityMap, tolerance) : "");
        } else if (exp instanceof SelectClause) {
            return ((SelectClause)exp).getActualIdentifier() + " " +
                    translateExpression(((SelectClause)exp).getSelectExpression(), entityMap, tolerance);
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
        } else if (exp instanceof AggregateFunction) {
            return ((AggregateFunction) exp).getActualIdentifier() + "(" +
                    translateExpression(((AggregateFunction) exp).getExpression(), entityMap, tolerance) + ")";
        } else if (exp instanceof GroupByClause) {
            return ((GroupByClause) exp).getActualIdentifier() + " " +
                    translateExpression(((GroupByClause) exp).getGroupByItems(), entityMap, tolerance);
        } else if (exp instanceof OrExpression orExpression) {
            return translateExpression(orExpression.getLeftExpression(), entityMap, tolerance) + " " +
                    orExpression.getActualIdentifier() + " " +
                    translateExpression(orExpression.getRightExpression(), entityMap, tolerance);
        } else if (exp instanceof HavingClause) {
            return ((HavingClause) exp).getActualIdentifier() + " " +
                    translateExpression(((HavingClause) exp).getConditionalExpression(), entityMap, tolerance);
        } else if (exp instanceof InExpression inExp) {
            return translateExpression(inExp.getExpression(), entityMap, tolerance) + " " +
                    (inExp.hasNot() ? inExp.getActualNotIdentifier() + " " : "") +
                    (inExp.hasInItems() ? inExp.getActualInIdentifier() + " " : "") +
                    translateExpression(inExp.getInItems(), entityMap, tolerance);
        } else if (exp instanceof ArithmeticExpression) {
            return translateExpression(((ArithmeticExpression) exp).getLeftExpression(), entityMap, tolerance) + " " +
                    ((ArithmeticExpression) exp).getActualIdentifier() + " " +
                    translateExpression(((ArithmeticExpression) exp).getRightExpression(), entityMap, tolerance);
        } else {
            if (tolerance.tolerance) {
                tolerance.violations += 1;
                return "";
            }
            throw new FailedTranslation();
        }
    }
}
