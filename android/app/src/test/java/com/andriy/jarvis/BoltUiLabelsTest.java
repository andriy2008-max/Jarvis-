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
        yes(BoltUiLabels.ridesHomeHeading("Тебе підвезти?"));
        yes(BoltUiLabels.ridesHomeHeading("Раді тебе бачити."));
        yes(BoltUiLabels.ridesHomeHeading("Good to see you"));
        yes(BoltUiLabels.ridesHomeFallback(true,true,true,true,true));
        yes(!BoltUiLabels.ridesHomeHeading("Маршрут"));
        yes(BoltUiLabels.ridesHomeFallback(false,true,true,true,true));
        yes(BoltUiLabels.ridesHomeFallback(false,true,true,false,true));
        yes(BoltUiLabels.ridesHomeFallback(true,true,true,false,false));
        yes(!BoltUiLabels.ridesHomeFallback(false,false,true,false,true));
        yes(!BoltUiLabels.ridesHomeFallback(true,false,false,false,true));
        yes(BoltUiLabels.RIDES_TAP_DELAY_MS==4000L);
        yes(!BoltUiLabels.ridesTapDue(0L));
        yes(!BoltUiLabels.ridesTapDue(3999L));
        yes(BoltUiLabels.ridesTapDue(4000L));
        yes(BoltUiLabels.ridesTapDue(4100L));
        for(String safe:new String[]{"Поїздки", "Рушаймо", "Тебе підвезти?",
            "Bolt Food", "Запланувати", "Куди поїдемо?", ""})
            yes(!BoltUiLabels.unsafeForTimedRidesTap(safe));
        for(String risky:new String[]{"Маршрут", "Підтвердити поїздку",
            "Підтвердити", "Оплатити", "Спосіб оплати", "Payment",
            "Checkout", "Confirm ride", "Trip details", "Замовити автомобіль"})
            yes(BoltUiLabels.unsafeForTimedRidesTap(risky));
        int[] rides=BoltUiLabels.screenshotRidesPoint(684,1536);
        yes(rides!=null && rides[0]==182 && rides[1]==489);
        // Actual Nov 14 Bolt screenshot: top-left tile x=27..335,y=386..589.
        // Old y=321 was the heading and never opened the rides flow.
        yes(rides[0]>=27 && rides[0]<=335 && rides[1]>=386 && rides[1]<=589);
        yes(rides[1]>430 && rides[1]<590);
        yes(BoltUiLabels.screenshotRidesPoint(1536,684)==null);
        yes(BoltUiLabels.screenshotRidesPoint(684,900)==null);
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
