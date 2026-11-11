package com.andriy.jarvis;

public final class BoltUiLabelsTest {
    private static void yes(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        yes(BoltUiLabels.rides("Поїздки"));
        yes(BoltUiLabels.rides("Rides"));
        yes(!BoltUiLabels.rides("Історія поїздок"));
        yes(!BoltUiLabels.rides("Замовити авто"));
        yes(BoltUiLabels.destination("Куди поїдемо?"));
        yes(BoltUiLabels.destination("destination_input"));
        yes(!BoltUiLabels.destination("Підтвердити"));
        yes(BoltUiLabels.pickup("Звідки?"));
        yes(BoltUiLabels.pickup("Pickup location"));
        yes(!BoltUiLabels.pickup("Куди?"));
        System.out.println("PASS BoltUiLabelsTest");
    }
}
