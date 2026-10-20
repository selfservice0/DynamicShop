package org.minecraftsmp.dynamicshop.managers;

import org.junit.Test;
import static org.junit.Assert.*;

public class CommandRewardOwnershipTest {
    @Test public void permanentPlayerGrantsRetainTheirWorldScope() {
        assertEquals(new CommandRewardOwnership("group.endupgrade1", null, null),
                CommandRewardOwnership.parse("lp user {player} permission set group.endupgrade1 true"));
        assertEquals(new CommandRewardOwnership("enderchest.rows.3", null, "nether"),
                CommandRewardOwnership.parse(" /luckperms:lp user {uuid} permission set enderchest.rows.3 world=nether "));
        assertEquals(new CommandRewardOwnership(null, "EndUpgrade1", "world"),
                CommandRewardOwnership.parse("luckperms user {player} parent add EndUpgrade1 world=world"));
    }

    @Test public void unrelatedTemporaryNegativeOrUnsupportedContextCommandsAreNotGuessed() {
        for (String command : new String[] {null, "", "say hello", "lp user SomeoneElse permission set test true",
                "lp user {player} permission set test false", "lp user {player} permission settemp test true 1d",
                "lp user {player} parent addtemp vip 1d", "lp group vip permission set test true",
                "lp user {player} permission set test true server=lobby",
                "lp user {player} permission set test true world=world server=lobby",
                "lp user {player} permission set test true world="}) {
            assertNull(command, CommandRewardOwnership.parse(command));
        }
    }
}
