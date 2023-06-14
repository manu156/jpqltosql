package com.github.manu156.jpqltosql.Action;

import com.github.manu156.jpqltosql.Entity.EntityMap;
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

import static com.github.manu156.jpqltosql.Util.PsiProcessUtil.getKeyValueMap;

public class ToSqlAction extends AnAction {
    @Override
    public void update(AnActionEvent e) {
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        Caret caret = e.getData(CommonDataKeys.CARET);
        if (null == psiFile || null == caret) {
            e.getPresentation().setEnabled(false);
            return;
        }
        e.getPresentation().setEnabled(null != psiFile.findElementAt(caret.getOffset()));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
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
            name = vp.get("name");
            String query = vp.get("query");
            if (!vp.containsKey("name") || !vp.containsKey("query"))
                throw new QueryNotFound();

            JPQLExpression jpql = new JPQLExpression(query, new JPQLGrammar3_1());
            String sql = translateExpression(jpql.getQueryStatement(), new EntityMap(e.getRequiredData(CommonDataKeys.PROJECT)));
            CopyPasteManager.getInstance().setContents(new StringSelection(sql));
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("JPQL2SQL")
                    .createNotification("Copied to SQL: " + name, NotificationType.INFORMATION)
                    .notify(e.getRequiredData(CommonDataKeys.PROJECT));
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

    private String translateExpression(Expression exp, EntityMap entityMap) {
        if (exp instanceof NumericLiteral) {
            return ((NumericLiteral)exp).getText();
        } else if (exp instanceof StateFieldPathExpression) {
            StateFieldPathExpression pathExp = (StateFieldPathExpression) exp;
            String cl = entityMap.getClassByAlias(pathExp.getIdentificationVariable().toParsedText());
            return pathExp.getIdentificationVariable() + "." + entityMap.getColumnNameByClassAndField(cl, pathExp.getPath(1));
        } else if (exp instanceof SubExpression) {
            return "(" + translateExpression(((SubExpression) exp).getExpression(), entityMap) + ")";
        } else if (exp instanceof InputParameter) {
            return exp.toParsedText();
        } else if (exp instanceof IdentificationVariable) {
            return exp.toParsedText();
        } else if (exp instanceof ConstructorExpression) {
            ConstructorExpression consExp = (ConstructorExpression) exp;
            return translateExpression(consExp.getConstructorItems(), entityMap);
        } else if (exp instanceof CollectionExpression) {
            CollectionExpression constructorItems = (CollectionExpression) exp;
            StringBuilder selectedColumnsStr = new StringBuilder();
            for (int i=0; i<constructorItems.childrenSize(); i++) {
                selectedColumnsStr.append(translateExpression(constructorItems.getChild(i), entityMap));
                if (i < constructorItems.childrenSize()-1 && constructorItems.hasComma(i))
                    selectedColumnsStr.append(",");
                if (i < constructorItems.childrenSize()-1 && constructorItems.hasSpace(i))
                    selectedColumnsStr.append(" ");
            }
            return selectedColumnsStr.toString();
        } else if (exp instanceof FromClause) {
            FromClause fromClause = (FromClause) exp;
            StringBuilder res = new StringBuilder(fromClause.getActualIdentifier() + " ");
            if (((IdentificationVariableDeclaration)(fromClause).getDeclaration()).hasRangeVariableDeclaration()) {
                IdentificationVariableDeclaration ivd = ((IdentificationVariableDeclaration) fromClause.getDeclaration());
                String className = ((RangeVariableDeclaration) ivd.getRangeVariableDeclaration()).getRootObject().toActualText();
                res.append(entityMap.getTableByClass(className));
                res.append(" ");
                res.append(((RangeVariableDeclaration) ivd.getRangeVariableDeclaration()).getIdentificationVariable());
            }
            if (((IdentificationVariableDeclaration)fromClause.getDeclaration()).hasJoins()) {
                Expression joins = ((IdentificationVariableDeclaration)fromClause.getDeclaration()).getJoins();
                res.append(translateExpression(joins, entityMap));
            }
            return res.toString();
        } else if (exp instanceof IdentificationVariableDeclaration) {
            throw new RuntimeException("not def"); //todo

        } else if (exp instanceof ComparisonExpression) {
            ComparisonExpression compExp = (ComparisonExpression) exp;
            if (!compExp.hasLeftExpression() || !compExp.hasRightExpression())
                throw new RuntimeException("no lr");
            String compOp = compExp.getComparisonOperator();
            String lTranslated = translateExpression(compExp.getLeftExpression(), entityMap);
            String rTranslated = translateExpression(compExp.getRightExpression(), entityMap);
            return " " + lTranslated + compOp + rTranslated;
        } else if (exp instanceof Join) {
            Join join = (Join) exp;
            OnClause onClause = (OnClause)join.getOnClause();

            return " " + join.getActualIdentifier() +
            " " + entityMap.getTableByClass(join.getJoinAssociationPath().toParsedText()) +
            " " + join.getIdentificationVariable().toParsedText() +
            " " + onClause.getActualIdentifier() +
            translateExpression(onClause.getConditionalExpression(), entityMap);
        } else if (exp instanceof WhereClause) {
            StringBuilder res = new StringBuilder();
            Expression t = ((WhereClause) exp).getConditionalExpression();
            while (true) {
                if (t instanceof AndExpression) {
                    res.insert(0, translateExpression(((AndExpression) t).getRightExpression(), entityMap));
                    res.insert(0, " " + ((AndExpression) t).getActualIdentifier() + " ");
                    t = ((AndExpression) t).getLeftExpression();
                } else {
                    res.insert(0, translateExpression(t, entityMap));
                    break;
                }
            }
            res.insert(0, ((WhereClause)exp).getActualIdentifier() + " ");
            return res.toString();
        } else if (exp instanceof AndExpression) {
            AndExpression andExpression = (AndExpression) exp;
            return translateExpression(andExpression.getLeftExpression(), entityMap) + " " +
                    andExpression.getActualIdentifier() + " " +
                    translateExpression(andExpression.getRightExpression(), entityMap);
        } else if (exp instanceof SelectStatement) {
            SelectStatement s = (SelectStatement) exp;
            if (s.hasFromClause())
                entityMap.populateAliasMap((FromClause) s.getFromClause());

            return (s.hasSelectClause() ? translateExpression(s.getSelectClause(), entityMap) + " " : "") +
                    (s.hasFromClause() ? translateExpression(s.getFromClause(), entityMap) + " " : "") +
                    (s.hasWhereClause() ? translateExpression(s.getWhereClause(), entityMap) + " " : "") +
                    (s.hasGroupByClause() ? translateExpression(s.getGroupByClause(), entityMap) + " " : "") +
                    (s.hasHavingClause() ? translateExpression(s.getHavingClause(), entityMap) + " " : "") +
                    (s.hasOrderByClause() ? translateExpression(s.getOrderByClause(), entityMap) : "");
        } else if (exp instanceof SelectClause) {
            return ((SelectClause)exp).getActualIdentifier() + " " +
                    translateExpression(((SelectClause)exp).getSelectExpression(), entityMap);
        } else if (exp instanceof StringLiteral) {
            return exp.toParsedText();
        } else if (exp instanceof NullComparisonExpression) {
            NullComparisonExpression nullCompExp = (NullComparisonExpression) exp;
            return translateExpression((nullCompExp).getExpression(), entityMap) + " " +
                    nullCompExp.getActualIsIdentifier() + " " +
                    (nullCompExp.hasNot() ? nullCompExp.getActualNotIdentifier() + " " : "") +
                    nullCompExp.getActualNullIdentifier();
        } else if (exp instanceof BetweenExpression) {
            BetweenExpression btwExp = (BetweenExpression) exp;
            return translateExpression(btwExp.getExpression(), entityMap) + " " +
                    (btwExp.hasNot() ? btwExp.getActualNotIdentifier() + " " : "") +
                    btwExp.getActualBetweenIdentifier() + " " +
                    translateExpression(btwExp.getLowerBoundExpression(), entityMap) + " " +
                    translateExpression(btwExp.getUpperBoundExpression(), entityMap);
        } else if (exp instanceof ResultVariable) {
            ResultVariable resExp = (ResultVariable) exp;
            return translateExpression(resExp.getSelectExpression(), entityMap) + " " +
                    resExp.getActualAsIdentifier() + " " +
                    resExp.getResultVariable().toParsedText();
        } else if (exp instanceof AggregateFunction) {
            return ((AggregateFunction) exp).getActualIdentifier() + "(" +
                    translateExpression(((AggregateFunction) exp).getExpression(), entityMap) + ")";
        } else if (exp instanceof GroupByClause) {
            return ((GroupByClause) exp).getActualIdentifier() + " " +
                    translateExpression(((GroupByClause) exp).getGroupByItems(), entityMap);
        } else if (exp instanceof OrExpression) {
            OrExpression orExpression = (OrExpression) exp;
            return translateExpression(orExpression.getLeftExpression(), entityMap) + " " +
                    orExpression.getActualIdentifier() + " " +
                    translateExpression(orExpression.getRightExpression(), entityMap);
        } else if (exp instanceof HavingClause) {
            return ((HavingClause) exp).getActualIdentifier() + " " +
                    translateExpression(((HavingClause) exp).getConditionalExpression(), entityMap);
        } else if (exp instanceof InExpression) {
            InExpression inExp = (InExpression) exp;
            return translateExpression(inExp.getExpression(), entityMap) + " " +
                    (inExp.hasNot() ? inExp.getActualNotIdentifier() + " " : "") +
                    (inExp.hasInItems() ? inExp.getActualInIdentifier() + " " : "") +
                    translateExpression(inExp.getInItems(), entityMap);
        } else if (exp instanceof ArithmeticExpression) {
            return translateExpression(((ArithmeticExpression) exp).getLeftExpression(), entityMap) + " " +
                    ((ArithmeticExpression) exp).getActualIdentifier() + " " +
                    translateExpression(((ArithmeticExpression) exp).getRightExpression(), entityMap);
        } else {
            // todo
            System.out.println("catch");
        }
        return "";
    }
}
