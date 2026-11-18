package com.andriy.jarvis;

public final class JarvisWatchMapsCommandsTest {
    private static void ok(boolean yes) {
        if(!yes)throw new AssertionError("Maps contract failed");
    }
    public static void main(String[] args) {
        ok(JarvisWatchMapsCommands.parse("Де я").whereAmI);
        ok(JarvisWatchMapsCommands.parse("Джарвіс, де я зараз").whereAmI);
        ok(JarvisWatchMapsCommands.parse("Покажи моє місцезнаходження").whereAmI);
        JarvisWatchMapsCommands.Request first=
            JarvisWatchMapsCommands.parse("Знайди дорогу до Стебник, вул. Шевченка 10");
        ok(first!=null && !first.whereAmI);
        ok("Стебник, вул. Шевченка 10".equals(first.destination));
        JarvisWatchMapsCommands.Request walk=
            JarvisWatchMapsCommands.parse("Джарвіс, проклади маршрут до Apartel Skhidnytsya пішки");
        ok(walk!=null && "Apartel Skhidnytsya".equals(walk.destination));
        ok("Львів, площа Ринок 1".equals(
            JarvisWatchMapsCommands.parse(
                "Побудуй маршрут до Львів, площа Ринок 1.").destination));
        ok(JarvisWatchMapsCommands.parse("Знайди дорогу до")==null);
        ok(JarvisWatchMapsCommands.parse("Знайди дорогу до пішки")==null);
        ok(JarvisWatchMapsCommands.parse("Знайди дорогу до https://site.com")==null);
        ok(JarvisWatchMapsCommands.parse("Знайди дорогу до вулиця\\nдруга")==null);
        ok(JarvisWatchMapsCommands.parse("Натисни оплатити")==null);
        ok(JarvisWatchMapsCommands.parse("Відкрий Google Карти")==null);
        System.out.println("PASS JarvisWatchMapsCommandsTest");
    }
}
