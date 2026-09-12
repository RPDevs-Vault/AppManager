// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Process;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

import io.github.muntashirakon.test.shadows.ShadowOpsDependencies;
import io.github.muntashirakon.test.shadows.ShadowOpsDependencies.ShadowAdb;
import io.github.muntashirakon.test.shadows.ShadowOpsDependencies.ShadowServer;
import io.github.muntashirakon.test.shadows.ShadowOpsDependencies.ShadowServices;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, shadows = {
        ShadowOpsDependencies.ShadowRoot.class,
        ShadowOpsDependencies.ShadowAdb.class,
        ShadowOpsDependencies.ShadowPermissions.class,
        ShadowOpsDependencies.ShadowUsers.class,
        ShadowOpsDependencies.ShadowServices.class,
        ShadowOpsDependencies.ShadowServer.class,
})
@LooperMode(LooperMode.Mode.PAUSED)
public class OpsWirelessDebuggingTest {
    private Context mContext;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        ShadowOpsDependencies.reset();
        ReflectionHelpers.setStaticField(Ops.class, "sDirectRoot", false);
        ReflectionHelpers.setStaticField(Ops.class, "sIsAdb", false);
        ReflectionHelpers.setStaticField(Ops.class, "sIsSystem", false);
        ReflectionHelpers.setStaticField(Ops.class, "sIsRoot", false);
        Ops.setWorkingUid(Process.myUid());
        Ops.setMode(Ops.MODE_ADB_WIFI);
    }

    @Test
    public void wirelessModeRequestsAutoConnectWhenWirelessDebuggingIsAvailable() {
        ShadowAdb.wirelessDebuggingEnabled = true;

        assertEquals(Ops.STATUS_AUTO_CONNECT_WIRELESS_DEBUGGING, Ops.init(mContext, true));
        assertTrue(Ops.isAdb());

        assertEquals(Ops.STATUS_SUCCESS, Ops.autoConnectWirelessDebugging(mContext));
        assertEquals(Ops.SHELL_UID, Ops.getWorkingUid());
        assertTrue(Ops.isAdb());
    }

    @Test
    public void wirelessModeRequestsChooserWhenWirelessDebuggingCannotBeEnabled() {
        ShadowAdb.wirelessDebuggingEnabled = false;

        assertEquals(Ops.STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED, Ops.init(mContext, true));
        assertTrue(Ops.isAdb());
        assertFalse(ShadowServices.alive);
    }

    @Test
    public void autoConnectFailureFallsBackToChooser() {
        ShadowServer.restartFailure = true;

        assertEquals(Ops.STATUS_AUTO_CONNECT_WIRELESS_DEBUGGING, Ops.init(mContext, true));
        assertEquals(Ops.STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED,
                Ops.autoConnectWirelessDebugging(mContext));
        assertFalse(Ops.isAdb());
        assertEquals(Process.myUid(), Ops.getWorkingUid());
    }

    @Test
    public void autoConnectRequestsPairingWhenAdbReportsPairingRequired() {
        ShadowServer.pairingRequired = true;

        assertEquals(Ops.STATUS_AUTO_CONNECT_WIRELESS_DEBUGGING, Ops.init(mContext, true));
        assertEquals(Ops.STATUS_ADB_PAIRING_REQUIRED,
                Ops.autoConnectWirelessDebugging(mContext));
        assertFalse(Ops.isAdb());
        assertEquals(Process.myUid(), Ops.getWorkingUid());
    }

    @Test
    public void manualConnectAfterWirelessChooserReachesAdb() {
        ShadowAdb.wirelessDebuggingEnabled = false;
        assertEquals(Ops.STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED, Ops.init(mContext, true));

        assertEquals(Ops.STATUS_SUCCESS, Ops.connectAdb(mContext, 5555, Ops.STATUS_FAILURE));
        assertTrue(Ops.isAdb());
        assertEquals(Ops.SHELL_UID, Ops.getWorkingUid());
    }
}
