#!/usr/bin/env python3
"""Exercise production Duo rules and the real cached telephony sampler with fake services."""
from pathlib import Path
import shutil
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "app/src/main/java/io/github/yixing233/hyperduo"
sources = {
    "android/content/Context.java": """
package android.content;
public class Context {
    public int airplane;
    public android.telephony.SubscriptionManager subs = new android.telephony.SubscriptionManager();
    public android.telephony.TelephonyManager phone = new android.telephony.TelephonyManager();
    public Object getContentResolver() { return this; }
    public <T> T getSystemService(Class<T> type) {
        return type.cast(type == android.telephony.SubscriptionManager.class ? subs : phone);
    }
}
""",
    "android/provider/Settings.java": """
package android.provider;
public class Settings {
    public static class Global {
        public static final String AIRPLANE_MODE_ON = "airplane_mode_on";
        public static int getInt(Object resolver, String name, int fallback) {
            return ((android.content.Context) resolver).airplane;
        }
    }
}
""",
    "android/telephony/SubscriptionInfo.java": """
package android.telephony;
public record SubscriptionInfo(int id, int slot) {
    public int getSubscriptionId() { return id; }
    public int getSimSlotIndex() { return slot; }
}
""",
    "android/telephony/SubscriptionManager.java": """
package android.telephony;
public class SubscriptionManager {
    public static int dataId = 22;
    public java.util.List<SubscriptionInfo> active = java.util.List.of();
    public boolean denied;
    public int queries;
    public static int getDefaultDataSubscriptionId() { return dataId; }
    public java.util.List<SubscriptionInfo> getActiveSubscriptionInfoList() {
        queries++;
        if (denied) throw new SecurityException("permission denied");
        return active;
    }
}
""",
    "android/telephony/ServiceState.java": """
package android.telephony;
public class ServiceState {
    public int voice, data;
    public boolean emergency, legacy;
    public ServiceState(int voice, int data, boolean emergency) {
        this.voice=voice; this.data=data; this.emergency=emergency;
    }
    public int getState() { return voice; }
    public int getDataRegistrationState() {
        if (legacy) throw new UnsupportedOperationException();
        return data;
    }
    public int getDataRegState() { return data; }
    public boolean isEmergencyOnly() { return emergency; }
}
""",
    "android/telephony/TelephonyManager.java": """
package android.telephony;
public class TelephonyManager {
    public static final int SIM_STATE_UNKNOWN=0, SIM_STATE_ABSENT=1;
    public java.util.Map<Integer, TelephonyManager> lines = new java.util.HashMap<>();
    public ServiceState service;
    public int[] sims = {1, 1};
    public int getActiveModemCount() { return sims.length; }
    public int getSimState(int slot) { return sims[slot]; }
    public TelephonyManager createForSubscriptionId(int id) {
        return lines.computeIfAbsent(id, ignored -> new TelephonyManager());
    }
    public ServiceState getServiceState() { return service; }
}
""",
    "io/github/yixing233/hyperduo/DuoPolicyTest.java": """
package io.github.yixing233.hyperduo;
import android.content.Context;
import android.telephony.*;
import java.util.*;
import static io.github.yixing233.hyperduo.DuoPolicy.*;

public final class DuoPolicyTest {
    static int checks;
    static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    static ColorOsRadio.Sample sample(Context context) {
        return new ColorOsRadio().sample(context, 0);
    }
    public static void main(String[] args) {
        check(charge(2, 1) == Charge.CHARGING, "real charging");
        check(charge(4, 1) == Charge.PAUSED, "connected and explicitly not charging");
        check(charge(3, 1) == Charge.PAUSED, "connected but discharging");
        check(charge(5, 1) == Charge.FULL, "full is separate from paused");
        check(charge(3, 0) == Charge.DISCONNECTED, "unplugged");
        check(charge(1, 1) == Charge.UNKNOWN, "unknown is not a fabricated pause");
        check(service(0, 1, false) == Radio.AVAILABLE, "voice service");
        check(service(1, 0, false) == Radio.AVAILABLE, "packet-data-only service");
        check(service(1, 1, true) == Radio.EMERGENCY_ONLY, "explicit emergency-only");
        check(service(1, 1, false) == Radio.NO_SERVICE, "both registrations unavailable");
        check(service(1, -1, false) == Radio.UNKNOWN, "unknown data registration is not no-service");
        check(service(3, -1, false) == Radio.NO_SERVICE, "radio explicitly powered off");
        int[] physical = {1,4};
        check(Arrays.equals(automaticLevels(physical, 1), new int[]{4,1}), "data line on top");
        check(Arrays.equals(physical, new int[]{1,4}), "named physical slots stay untouched");
        check(primarySlot(0, new int[]{0,4}) == 0, "zero is a valid reading");
        check(primarySlot(0, new int[]{-1,2}) == 1, "absent data slot falls back");
        check(primarySlot(-1, new int[]{-1,-1}) == -1, "no fabricated primary");
        check(foldMobile(true, 2, Radio.AVAILABLE, false), "working drawn signals own native slots");
        check(!foldMobile(true, 2, Radio.NO_SERVICE, false), "warning not visible: keep native");
        check(foldMobile(true, 0, Radio.NO_SIM, true), "visible no-SIM warning owns native slot");
        check(!foldMobile(true, 2, Radio.UNKNOWN, true), "uncertain service preserves native");
        check(!foldMobile(true, 2, Radio.AIRPLANE, true), "airplane preserves system indicator");
        check(!foldMobile(false, 2, Radio.AVAILABLE, true), "hand-back always releases ownership");
        check((pausedColor(0xccffffff) >>> 24) == 0xcc, "paused style preserves native alpha");

        Context c = new Context();
        check(sample(c).radio == Radio.NO_SIM, "empty subscriptions plus absent SIMs");
        c.phone.sims[0] = 0;
        check(sample(c).radio == Radio.UNKNOWN, "modem initializing is not no-SIM");
        c.phone.sims[0] = 2;
        check(sample(c).radio == Radio.UNKNOWN, "locked or inactive SIM is not absent");
        c.subs.active = null;
        check(sample(c).radio == Radio.UNKNOWN, "null subscription query is unknown");
        c.subs.active = List.of(new SubscriptionInfo(11,0), new SubscriptionInfo(22,1));
        c.phone.createForSubscriptionId(11).service = new ServiceState(0,0,false);
        c.phone.createForSubscriptionId(22).service = new ServiceState(1,1,false);
        ColorOsRadio.Sample selected = sample(c);
        check(selected.dataSlot == 1 && selected.radio == Radio.NO_SERVICE,
              "working secondary cannot conceal no service on selected data line");
        c.phone.createForSubscriptionId(22).service.emergency = true;
        check(sample(c).radio == Radio.EMERGENCY_ONLY, "real service sample preserves SOS");
        c.phone.createForSubscriptionId(22).service.data = 0;
        c.phone.createForSubscriptionId(22).service.emergency = false;
        check(sample(c).radio == Radio.AVAILABLE, "data registration recovers service");
        c.phone.createForSubscriptionId(22).service.legacy = true;
        check(sample(c).radio == Radio.AVAILABLE, "legacy data-registration accessor");
        c.phone.createForSubscriptionId(22).service = null;
        check(sample(c).radio == Radio.UNKNOWN, "unreadable selected line keeps native warning");
        c.subs.denied = true;
        check(sample(c).radio == Radio.UNKNOWN, "denied telephony does not fabricate a warning");
        c.airplane = 1;
        check(sample(c).radio == Radio.AIRPLANE, "airplane is separate from permission failure");
        c.airplane = 0;
        c.subs.denied = false;
        c.phone.createForSubscriptionId(22).service = new ServiceState(0,0,false);
        ColorOsRadio cached = new ColorOsRadio();
        int before = c.subs.queries;
        cached.sample(c, 0);
        c.phone.createForSubscriptionId(22).service = new ServiceState(1,1,true);
        check(cached.sample(c, 999).radio == Radio.AVAILABLE, "all hosts share a bounded snapshot");
        check(c.subs.queries == before+1, "no repeated IPC inside sampling interval");
        check(cached.sample(c, 1000).radio == Radio.EMERGENCY_ONLY, "next sample sees service change");
        SubscriptionManager.dataId = 11;
        check(cached.sample(c, 2000).dataSlot == 0, "data-line switch refreshes without restart");
        c.subs.active = List.of();
        c.phone.sims = new int[]{1,1};
        check(cached.sample(c, 3000).radio == Radio.NO_SIM, "SIM removal clears old service");
        System.out.println("Duo policy and production telephony sampling passed: " + checks + " checks");
    }
}
""",
}

readings = (SOURCE / "ColorOsReadings.java").read_text()
methods = []
for signature in ("private View\\[\\] orderedViews", "private OutRingLayout.Plan plan",
                  "@Override protected void onLayout"):
    match = re.search(rf"^    {signature}\([^\n]*\) \{{.*?^    \}}", readings, re.M | re.S)
    if not match:
        raise RuntimeError(f"Production notice method missing: {signature}")
    methods.append(match[0].replace("@Override ", ""))
methods.append(re.search(r"^    boolean hasVisibleRadioNotice.*$", readings, re.M)[0])
sources["io/github/yixing233/hyperduo/DuoNoticeTest.java"] = r"""
package io.github.yixing233.hyperduo;
public final class DuoNoticeTest {
    static final int VISIBLE=0, GONE=8, LAYOUT_DIRECTION_RTL=1;
    static class View {
        int width=0, height=16, visibility=VISIBLE;
        float alpha=1;
        int getVisibility(){return visibility;}
        int getMeasuredWidth(){return width;}
        int getMeasuredHeight(){return height;}
        void setAlpha(float value){alpha=value;}
        void setPivotX(float value){}
        void setPivotY(float value){}
        void setScaleX(float value){}
        void setScaleY(float value){}
        void layout(int l,int t,int r,int b){}
    }
    static class Text extends View { String getText(){return "SOS";} }
    static class Appearance {int[] outOrder={0,1,2};}
    static class Prefs {static final int OUT_SLOT_SIGNAL=0,OUT_SLOT_READOUTS=1,OUT_SLOT_TYPE=2;}
    static class TrioHooks {
        static int[] outLabelMargins(Object view){return new int[]{0,0};}
        static int perfWidth(Text perf,String text){return perf.width;}
        static int outGapFor(int slot,Object view,int inward){return 0;}
        static int[] outRingDropOrder(Appearance appearance){return new int[]{2,1,0};}
    }
    static class ColorOsHooks {static int changes;static void invalidateHosts(){changes++;}}
    Appearance appearance=new Appearance();
    View signal=new View();
    Text perf=new Text(),type=new Text();
    boolean radioNotice=true,radioNoticeVisible;
    int width=35;
    int getLayoutDirection(){return 0;}
    int getWidth(){return width;}
    int getHeight(){return 16;}
@METHODS@
    static void check(boolean value,String message){
        if(!value)throw new AssertionError(message);
    }
    public static void main(String[] args){
        DuoNoticeTest row=new DuoNoticeTest();
        row.signal.width=40;row.perf.width=50;row.type.width=30;
        check(!row.hasVisibleRadioNotice(),"do not claim a warning before layout");
        OutRingLayout.Plan narrow=row.plan(35,16);
        check(narrow.widths[2]==30 && narrow.widths[0]==0 && narrow.widths[1]==0,
              "optional bars and power yield before the explicit warning");
        check(narrow.reserve==30 && narrow.scale==1f,"drop recalculates width without shrinking the warning");
        row.onLayout(true,0,0,35,16);
        check(row.hasVisibleRadioNotice(),"a laid-out warning can take ownership");
        check(row.type.alpha==1f && row.perf.alpha==0f,"dropped slots are actually invisible");
        check(DuoPolicy.foldMobile(true,0,DuoPolicy.Radio.NO_SIM,row.hasVisibleRadioNotice()),
              "native no-SIM can hide only after the warning is visible");
        row.width=0;
        row.onLayout(true,0,0,0,16);
        check(!row.hasVisibleRadioNotice(),"zero-width warning releases native immediately");
        check(!DuoPolicy.foldMobile(true,0,DuoPolicy.Radio.NO_SIM,row.hasVisibleRadioNotice()),
              "native indication survives a constrained layout");
        row.width=150;
        row.onLayout(true,0,0,150,16);
        check(row.hasVisibleRadioNotice(),"warning recovers when space returns");
        row.radioNotice=false;
        check(!row.hasVisibleRadioNotice(),"service recovery clears the notice gate before another layout");
        row.radioNotice=true;
        row.appearance.outOrder=new int[]{2,0,1};
        row.width=35;
        OutRingLayout.Plan reordered=row.plan(35,16);
        check(reordered.widths[0]==30 && reordered.reserve==30,"custom ordering retains warning priority");
        OutRingLayout.Plan empty=OutRingLayout.solve(50,0,0,0,16,new int[]{0,0},
                new int[]{0,0},new int[]{0,0},5,new int[]{0,1});
        check(empty.reserve==0,"an empty strip reserves no leftover margin");
        System.out.println("Production warning layout, priority and native fallback passed");
    }
}
""".replace("@METHODS@", "\n".join(methods))

with tempfile.TemporaryDirectory(prefix="colorduo-duo-") as directory:
    root = Path(directory)
    files = []
    for name, content in sources.items():
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
        files.append(str(path))
    compiler = ["javac"] if shutil.which("javac") else ["java", "-m", "jdk.compiler/com.sun.tools.javac.Main"]
    subprocess.run(compiler + ["-encoding", "UTF-8", "-d", str(root)]
                   + [str(SOURCE / name) for name in ("DuoPolicy.java", "ColorOsRadio.java", "Refl.java", "OutRingLayout.java")]
                   + files, check=True)
    subprocess.run(["java", "-cp", str(root), "io.github.yixing233.hyperduo.DuoPolicyTest"], check=True)
    subprocess.run(["java", "-cp", str(root), "io.github.yixing233.hyperduo.DuoNoticeTest"], check=True)
