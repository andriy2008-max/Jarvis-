package com.andriy.jarvis;

public final class BoltUiLabelsTest {
    private static void yes(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        yes(BoltUiLabels.rides("Поїздки"));
        yes(BoltUiLabels.rides("Rides"));
        yes(BoltUiLabels.rides("Поїздки • Рушаймо!"));
        yes(BoltUiLabels.rides("Поїздки\nРушаймо!"));
        yes(!BoltUiLabels.rides("Поїздки завтра"));
        yes(!BoltUiLabels.rides("Мої поїздки"));
        yes(!BoltUiLabels.rides("Історія поїздок"));
        yes(!BoltUiLabels.rides("Замовити авто"));
        yes(BoltUiLabels.destination("Куди поїдемо?"));
        yes(BoltUiLabels.destination("destination_input"));
        yes(BoltUiLabels.destination("Місце висадки"));
        yes(BoltUiLabels.destination("Місце висадки, адреса"));
        yes(!BoltUiLabels.destination("Місце посадки"));
        yes(BoltUiLabels.routeTitle("Маршрут"));
        yes(BoltUiLabels.routeTitle("Route"));
        yes(!BoltUiLabels.routeTitle("Мої поїздки"));
        int[] destination = BoltUiLabels.screenshotDropoffPoint(684,1536);
        yes(destination != null && Math.abs(destination[0]-250)<=1 &&
            Math.abs(destination[1]-322)<=1);
        yes(!BoltUiLabels.dropoffRegion(200,240,684,1536));
        yes(BoltUiLabels.screenshotDropoffPoint(1536,684)==null);
        yes(!BoltUiLabels.destination("Підтвердити"));
        yes(BoltUiLabels.typedDestinationMatches("вулиця Шевченка 5", "вулиця Шевченка 5"));
        yes(!BoltUiLabels.typedDestinationMatches("вулиця Шевченка 5", "вулиця Шевченка 50"));
        yes(!BoltUiLabels.typedDestinationMatches("вулиця Шевченка 5", "інша адреса"));
        yes(!BoltUiLabels.typedDestinationMatches("", ""));
        yes(!BoltUiLabels.typedDestinationMatches("вулиця Шевченка 5", null));
        yes(BoltUiLabels.pickup("Звідки?"));
        yes(BoltUiLabels.pickup("Pickup location"));
        yes(!BoltUiLabels.pickup("Куди?"));
        System.out.println("PASS BoltUiLabelsTest");
    }
}
