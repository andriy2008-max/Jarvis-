package com.andriy.jarvis;

/** Never discard an encrypted Watch7 finance command on a transient phone failure.
 * Only explicit non-retriable validation errors may clear the queue. */
public final class FinanceDeliveryPolicy {
    private FinanceDeliveryPolicy(){}
    public static boolean finalRejection(String detail) {
        if(detail==null)return false;
        String text=detail.trim().toLowerCase(java.util.Locale.forLanguageTag("uk-UA"));
        return text.startsWith("не розпізнав фінансовий запис") ||
            text.startsWith("конфлікт ідентифікатора команди") ||
            text.startsWith("для боргів та активів потрібен окремий тип обліку") ||
            text.startsWith("перевір дату й час годинника") ||
            text.startsWith("голосовий запис гривень потребує рахунку uah");
    }
}
