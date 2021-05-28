package no.h_nh.docker_step.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.sun.security.auth.module.UnixSystem;


public class MiscToolsTest {

    @Test
    public void getAgentUser() {
        final String agentUser = MiscTools.getAgentUser();
        final String sysUser = String.format("%d:%d",
                new UnixSystem().getUid(), new UnixSystem().getGid());
        assertEquals("Failed to fetch agent user", sysUser, agentUser);
    }
}
