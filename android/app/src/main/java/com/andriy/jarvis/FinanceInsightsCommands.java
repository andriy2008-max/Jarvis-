package com.andriy.jarvis;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Explicit, deterministic, read-only financial questions and budget settings.
 * Never infers transactions, reads another app's private database, or sends
 * financial text to a generative model. Android-free for JVM contract tests.
 */
public final class FinanceInsightsCommands {
    public enum Kind {
        SPENT_TODAY, SPENT_MONTH, SPENT_CATEGORY_TODAY, SPENT_CATEGORY_MONTH,
        BALANCES, ACCOUNT_BALANCE, SET_BUDGET, SET_CATEGORY_BUDGET,
        SHOW_BUDGET, SHOW_CATEGORY_BUDGET, BUDGET_REMAINING
    }

    public static final class Request {
        public final Kind kind;
        public final String category;
        public final String account;
        public final String amount;

        Request(Kind kind, String category, String account, String amount) {
            this.kind = kind;
            this.category = category;
            this.account = account;
            this.amount = amount;
        }
        public boolean changesBudget() {
            return kind == Kind.SET_BUDGET || kind == Kind.SET_CATEGORY_BUDGET;
        }
    }

    private static final String NUMBER = "([0-9]{1,7}(?:[,.][0-9]{1,2})?)";
    private static final String CURRENCY = "(?:\\s*(?:грн\\.?|гривень|гривні|гривня))?";
    private static final String SET = "(?:встанови|постав|задай|зміни|онови)";
    private static final Pattern SET_CATEGORY = Pattern.compile(
        "^" + SET + "\\s+(?:місячний\\s+)?бюджет\\s+на\\s+(.+?)\\s+" +
            NUMBER + CURRENCY + "$");
    private static final Pattern SET_TOTAL = Pattern.compile(
        "^" + SET + "\\s+(?:місячний\\s+)?бюджет\\s+" +
            "(?:(?:на\\s+(?:цей|поточний)\\s+місяць|на\\s+місяць)\\s+)?" +
            NUMBER + CURRENCY + "(?:\\s+на\\s+місяць)?$");
    private static final Pattern SHOW_CATEGORY = Pattern.compile(
        "^(?:який|покажи|назви)\\s+(?:мій\\s+)?бюджет\\s+на\\s+(.+)$");
    private static final Pattern SPEND_CATEGORY = Pattern.compile(
        "^(?:скільки\\s+(?:я\\s+)?витратив|скільки\\s+витрачено|витрати)" +
            "\\s+(?:на|за\\s+категорією)\\s+(.+?)" +
            "(?:\\s+(сьогодні|за\\s+сьогодні|цього\\s+місяця|за\\s+місяць))?$");

    private FinanceInsightsCommands() {}

    private static Request request(Kind k, String cat, String account, String amount) {
        return new Request(k,cat,account,amount);
    }

    public static Request parse(String raw) {
        if (raw == null || raw.length() > 250) return null;
        String s = CommandPatterns.normalize(raw)
            .replaceAll("\\s+"," ").trim();
        if (s.isEmpty()) return null;

        Matcher setCat = SET_CATEGORY.matcher(s);
        if (setCat.matches()) {
            String category = expenseCategory(setCat.group(1));
            String amount = positiveAmount(setCat.group(2));
            // "на цей місяць" is not a category; let the general-budget
            // matcher below handle it instead of rejecting the command.
            if(category!=null && amount!=null)
                return request(Kind.SET_CATEGORY_BUDGET,category,null,amount);
        }
        Matcher setAll = SET_TOTAL.matcher(s);
        if (setAll.matches()) {
            String amount = positiveAmount(setAll.group(1));
            return amount == null ? null :
                request(Kind.SET_BUDGET,null,null,amount);
        }

        if (s.matches("^(?:який|покажи|назви)\\s+(?:мій\\s+)?бюджет" +
                      "(?:\\s+(?:на\\s+)?(?:цей|поточний)\\s+місяць|\\s+на\\s+місяць)?$"))
            return request(Kind.SHOW_BUDGET,null,null,null);

        Matcher showCategory = SHOW_CATEGORY.matcher(s);
        if (showCategory.matches()) {
            String category=expenseCategory(showCategory.group(1));
            if(category!=null)
                return request(Kind.SHOW_CATEGORY_BUDGET,category,null,null);
        }

        if (s.matches("^скільки\\s+(?:ще\\s+)?(?:залишилось|залишилося|лишилося)" +
                      "\\s+(?:до\\s+)?бюджету" +
                      "(?:\\s+(?:цього\\s+місяця|на\\s+місяць))?$") ||
            s.matches("^(?:який|покажи)\\s+залишок\\s+бюджету$"))
            return request(Kind.BUDGET_REMAINING,null,null,null);

        if(s.matches("^(?:скільки\\s+(?:я\\s+)?витратив|скільки\\s+витрачено)" +
                     "\\s+(?:за\\s+)?сьогодні$") ||
           s.matches("^витрати\\s+(?:за\\s+)?сьогодні$"))
            return request(Kind.SPENT_TODAY,null,null,null);

        if(s.matches("^(?:скільки\\s+(?:я\\s+)?витратив|скільки\\s+витрачено)" +
                     "\\s+(?:за\\s+)?(?:цей|поточний)\\s+місяць$") ||
           s.matches("^(?:скільки\\s+(?:я\\s+)?витратив|скільки\\s+витрачено)" +
                     "\\s+цього\\s+місяця$") ||
           s.matches("^витрати\\s+(?:за\\s+)?(?:цей|поточний)\\s+місяць$") ||
           s.matches("^витрати\\s+цього\\s+місяця$"))
            return request(Kind.SPENT_MONTH,null,null,null);

        Matcher spendCategory = SPEND_CATEGORY.matcher(s);
        if(spendCategory.matches()) {
            String category=expenseCategory(spendCategory.group(1));
            if(category==null)return null;
            String period=spendCategory.group(2);
            boolean today=period!=null && period.contains("сьогодні");
            return request(today ? Kind.SPENT_CATEGORY_TODAY :
                Kind.SPENT_CATEGORY_MONTH,category,null,null);
        }

        if(s.matches("^(?:покажи\\s+)?(?:залишки|баланси)" +
                     "(?:\\s+(?:на\\s+)?рахунків)?$") ||
           s.matches("^(?:який|покажи)\\s+(?:мій\\s+)?баланс$") ||
           s.matches("^скільки\\s+(?:в\\s+мене\\s+)?грошей\\s+на\\s+рахунках$"))
            return request(Kind.BALANCES,null,null,null);

        if(s.matches("^(?:скільки\\s+грошей|який\\s+залишок|покажи\\s+баланс)" +
                     "\\s+(?:на|в|у)\\s+.+$")) {
            String account=null;
            if(s.contains("карт") && s.contains("моно"))account="карта моно";
            else if(s.contains("готів"))account="готівка";
            else if(s.contains("заначк"))account="заначка гривні";
            if(account!=null)return request(Kind.ACCOUNT_BALANCE,null,account,null);
        }
        return null;
    }

    private static String positiveAmount(String raw) {
        try {
            BigDecimal number=new BigDecimal(raw.replace(',','.'));
            if(number.signum()<=0 || number.compareTo(new BigDecimal("999999.99"))>0)
                return null;
            return number.stripTrailingZeros().toPlainString();
        } catch(RuntimeException error) {
            return null;
        }
    }

    private static String expenseCategory(String raw) {
        return FinanceVoiceCommands.category(FinanceVoiceCommands.Kind.EXPENSE,
            raw.replaceAll("\\s+(?:цього\\s+місяця|за\\s+місяць)$",""));
    }
}
