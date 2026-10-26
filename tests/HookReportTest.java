package io.github.yixing233.hyperduo;

/** The adapter summary line is what a log reader sees first; its shape must not drift. */
public final class HookReportTest {
    public static void main(String[] args) {
        check("compat adapter=0 hosts=0 wifi=0 mobile=0".equals(HookReport.render(false, 0)),
                "a fresh process reports zeroes");

        HookReport.adapterInstalled();
        check("compat adapter=1 hosts=0 wifi=0 mobile=0".equals(HookReport.render(false, 0)),
                "the adapter flag is independent of the hosts");

        HookReport.hostApplied();
        HookReport.hostApplied();
        check("compat adapter=1 hosts=2 wifi=1 mobile=3".equals(HookReport.render(true, 3)),
                "hosts are counted and the captures are reported");

        check(HookReport.firstHost(), "the summary is emitted once");
        check(!HookReport.firstHost(), "and never again in the same process");

        System.out.println("Hook report format passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
