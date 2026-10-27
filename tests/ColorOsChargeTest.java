package io.github.yixing233.hyperduo;

import java.io.IOException;

/** Real device snapshots: both Android statuses are 2; native PLC changes from 3 to 2. */
public final class ColorOsChargeTest {
    private static int checks;

    private static final class Node implements ColorOsCharge.Reader {
        String value = "status=3\n";
        int reads;
        boolean denied;
        @Override public String read(String path) throws IOException {
            check(ColorOsCharge.PLC_PATH.equals(path), "only the verified read-only PLC node is queried");
            reads++;
            if (denied) throw new IOException("permission denied");
            return value;
        }
    }

    public static void main(String[] args) {
        Node node = new Node();
        ColorOsCharge sampler = new ColorOsCharge(node);
        check(sampler.sample(2, 1, 0).charge == DuoPolicy.Charge.PAUSED,
                "native bypass makes a CHARGING Android snapshot grey");
        check(node.reads == 1, "one shared observation");
        check(sampler.sample(2, 1, 300).charge == DuoPolicy.Charge.PAUSED && node.reads == 1,
                "repeated notifications do not read sysfs per frame");
        node.value = "status=2\n";
        check(sampler.sample(2, 1, 1000).charge == DuoPolicy.Charge.CHARGING,
                "turning bypass off restores the unchanged Android CHARGING snapshot");
        node.value = "status=3\n";
        check(sampler.sample(2, 2, 2000).charge == DuoPolicy.Charge.PAUSED, "USB wired power also supports bypass");
        check(sampler.sample(3, 0, 2001).charge == DuoPolicy.Charge.DISCONNECTED,
                "unplugging clears bypass immediately without waiting for the next sample");
        int before = node.reads;
        check(sampler.sample(2, 4, 2002).charge == DuoPolicy.Charge.CHARGING && node.reads == before,
                "wireless charging never inherits a wired bypass flag");
        node.value = "status=2";
        check(sampler.sample(2, 1, 2003).charge == DuoPolicy.Charge.CHARGING && node.reads > before,
                "replugging reads fresh state even inside the previous polling interval");
        node.value = "status=3";
        check(sampler.sample(5, 1, 3003).charge == DuoPolicy.Charge.PAUSED,
                "explicit bypass remains grey at 100 percent");
        node.denied = true;
        ColorOsCharge.Sample failed = sampler.sample(2, 1, 4003);
        check(failed.charge == DuoPolicy.Charge.CHARGING && failed.bypass == null,
                "read failure releases stale bypass and preserves Android state");
        check(sampler.sample(4, 1, 5003).charge == DuoPolicy.Charge.PAUSED,
                "ordinary charging pause still works when PLC is unavailable");
        check(sampler.sample(5, 1, 6003).charge == DuoPolicy.Charge.FULL,
                "unknown PLC never turns an ordinary full battery into bypass");
        node.denied = false;
        node.value = "status=3";
        check(sampler.sample(2, 1, 7003).charge == DuoPolicy.Charge.PAUSED, "transient read failures recover");
        for (String invalid : new String[]{null, "", "3", "status=0", "status=1", "status=4", "status=5",
                "status=30", "status=-3", "status=3junk", "status=3\nstatus=2", "status=3" + " ".repeat(65)}) {
            check(ColorOsCharge.parse(invalid) == null, "unknown or malformed PLC is not guessed: " + invalid);
        }
        check(Boolean.TRUE.equals(ColorOsCharge.parse("  status=3\n")), "surrounding node whitespace is accepted");
        check(Boolean.FALSE.equals(ColorOsCharge.parse("status=2\n")), "disabled PLC is definitive");
        check(DuoPolicy.charge(2, 0, true) == DuoPolicy.Charge.CHARGING,
                "PLC alone cannot assert an attached power source");
        check(DuoPolicy.charge(2, 4, true) == DuoPolicy.Charge.CHARGING,
                "policy also rejects stale wired bypass on wireless power");
        System.out.println("Native ColorOS PLC charge state and fallback passed: " + checks + " checks");
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
