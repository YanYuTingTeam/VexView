import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import lk.vexview.hud.HUDViewer;
import lk.vexview.hud.core.VexShow;

public final class VerifyHudLayers {
    public static void main(String[] args) throws Exception {
        Map<String, VexShow> original = new LinkedHashMap<>();
        Random random = new Random(29L);
        for (int i = 0; i < 1000; i++) {
            VexShow element = new VexShow() {
                public void m4() {}
            };
            element.f6 = i == 0 ? Integer.MAX_VALUE : i == 1 ? Integer.MIN_VALUE : random.nextInt(5) - 2;
            original.put("element" + i, element);
        }

        List<String> insertionOrder = new ArrayList<>(original.keySet());
        List<String> expected = new ArrayList<>(insertionOrder);
        expected.sort(Comparator.comparingInt(key -> original.get(key).f6));
        Method sort = HUDViewer.class.getDeclaredMethod("m7", Map.class);
        sort.setAccessible(true);
        Map<?, ?> sorted = (Map<?, ?>) sort.invoke(null, original);

        if (!expected.equals(new ArrayList<>(sorted.keySet())) || !original.equals(sorted)
                || !insertionOrder.equals(new ArrayList<>(original.keySet()))) {
            throw new AssertionError("Layer sorting changed equal-depth insertion order or map entries");
        }
        System.out.println("PASS: 1,000 entries, duplicate depths and full integer range, stable order");
    }
}
