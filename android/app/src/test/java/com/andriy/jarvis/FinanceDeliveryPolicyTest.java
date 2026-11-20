package com.andriy.jarvis;

public final class FinanceDeliveryPolicyTest {
    private static void check(boolean yes,String why) {
        if(!yes)throw new AssertionError("Finance queue: "+why);
    }
    public static void main(String[] args) {
        check(FinanceDeliveryPolicy.finalRejection("Не розпізнав фінансовий запис."),
            "reject invalid spoken ledger entry");
        check(FinanceDeliveryPolicy.finalRejection("Конфлікт ідентифікатора команди"),
            "reject conflicting UUID");
        check(FinanceDeliveryPolicy.finalRejection(
            "Для боргів та активів потрібен окремий тип обліку."),
            "do not requeue known unsupported entry");
        check(!FinanceDeliveryPolicy.finalRejection("Не вдалося зберегти фінансовий запис."),
            "temporary finance DB failure must preserve watch utterance");
        check(!FinanceDeliveryPolicy.finalRejection("Не вдалося прочитати фінанси Pixel."),
            "temporary locked DB failure must retain queue");
        check(!FinanceDeliveryPolicy.finalRejection(
            "Відкрий Фінанси у Джарвісі та додай рахунок"),
            "missing account can be fixed by the user");
        check(!FinanceDeliveryPolicy.finalRejection("Телефон не відповідає."),
            "transport failures never delete queued expense");
        check(!FinanceDeliveryPolicy.finalRejection(null),
            "unknown ACK errors never delete queued expense");
        System.out.println("PASS FinanceDeliveryPolicyTest");
    }
}
