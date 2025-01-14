/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.configuration.AtomicConfiguration;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.ConnectorConfiguration;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.configuration.WALMode;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.TestRecordingCommunicationSpi;
import org.apache.ignite.internal.commandline.CommandHandler;
import org.apache.ignite.internal.processors.cache.GridCacheFuture;
import org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxPrepareFuture;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearTxPrepareFutureAdapter;
import org.apache.ignite.internal.processors.cache.transactions.IgniteInternalTx;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.G;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.testframework.junits.SystemPropertiesRule;
import org.apache.ignite.testframework.junits.WithSystemProperty;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.junit.ClassRule;
import org.junit.rules.TestRule;

import static java.nio.file.Files.delete;
import static java.nio.file.Files.newDirectoryStream;
import static java.util.Arrays.asList;
import static java.util.Objects.nonNull;
import static org.apache.ignite.IgniteSystemProperties.IGNITE_BASELINE_AUTO_ADJUST_ENABLED;
import static org.apache.ignite.IgniteSystemProperties.IGNITE_ENABLE_EXPERIMENTAL_COMMAND;
import static org.apache.ignite.configuration.DataStorageConfiguration.DFLT_CHECKPOINT_FREQ;
import static org.apache.ignite.internal.processors.cache.verify.VerifyBackupPartitionsDumpTask.IDLE_DUMP_FILE_PREFIX;
import static org.apache.ignite.testframework.GridTestUtils.cleanIdleVerifyLogFiles;

/**
 * Common abstract class for testing {@link CommandHandler}.
 * I advise you to look at the heirs classes:
 * {@link GridCommandHandlerClusterPerMethodAbstractTest}
 * {@link GridCommandHandlerClusterByClassAbstractTest}
 */
@WithSystemProperty(key = IGNITE_BASELINE_AUTO_ADJUST_ENABLED, value = "false")
@WithSystemProperty(key = IGNITE_ENABLE_EXPERIMENTAL_COMMAND, value = "true")
public abstract class GridCommandHandlerAbstractTest extends GridCommonAbstractTest {
    /** */
    @ClassRule public static final TestRule classRule = new SystemPropertiesRule();

    /** */
    protected static final String CLIENT_NODE_NAME_PREFIX = "client";

    /** Option is used for auto confirmation. */
    protected static final String CMD_AUTO_CONFIRMATION = "--yes";

    /** System out. */
    protected static PrintStream sysOut;

    /**
     * Test out - can be injected via {@link #injectTestSystemOut()} instead of System.out and analyzed in test.
     * Will be as well passed as a handler output for an anonymous logger in the test.
     */
    protected static ByteArrayOutputStream testOut;

    /** Atomic configuration. */
    protected AtomicConfiguration atomicConfiguration;

    /** Additional data region configuration. */
    protected DataRegionConfiguration dataRegionConfiguration;

    /** Checkpoint frequency. */
    protected long checkpointFreq = DFLT_CHECKPOINT_FREQ;

    /** {@inheritDoc} */
    @Override protected void beforeTestsStarted() throws Exception {
        super.beforeTestsStarted();

        testOut = new ByteArrayOutputStream(16 * 1024);
        sysOut = System.out;
    }

    /** {@inheritDoc} */
    @Override protected void afterTestsStopped() throws Exception {
        super.afterTestsStopped();

        cleanIdleVerifyLogFiles();
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        super.afterTest();

        log.info("Test output for " + currentTestMethod());
        log.info("----------------------------------------");

        System.setOut(sysOut);

        log.info(testOut.toString());

        testOut.reset();
    }

    /** {@inheritDoc} */
    @Override public String getTestIgniteInstanceName() {
        return "gridCommandHandlerTest";
    }

    /**
     * @return Logger.
     */
    private Logger createTestLogger() {
        Logger log = CommandHandler.initLogger(null);

        // Adding logging to console.
        log.addHandler(CommandHandler.setupStreamHandler());

        return log;
    }

    /** */
    protected boolean idleVerifyRes(Path p) {
        return p.toFile().getName().startsWith(IDLE_DUMP_FILE_PREFIX);
    }

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String igniteInstanceName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(igniteInstanceName);

        if (atomicConfiguration != null)
            cfg.setAtomicConfiguration(atomicConfiguration);

        cfg.setCommunicationSpi(new TestRecordingCommunicationSpi());

        cfg.setConnectorConfiguration(new ConnectorConfiguration());

        DataStorageConfiguration memCfg = new DataStorageConfiguration()
            .setCheckpointFrequency(checkpointFreq)
            .setDefaultDataRegionConfiguration(new DataRegionConfiguration().setMaxSize(50L * 1024 * 1024));

        if (dataRegionConfiguration != null)
            memCfg.setDataRegionConfigurations(dataRegionConfiguration);

        cfg.setDataStorageConfiguration(memCfg);

        DataStorageConfiguration dsCfg = cfg.getDataStorageConfiguration();
        dsCfg.setWalMode(WALMode.LOG_ONLY);
        dsCfg.getDefaultDataRegionConfiguration().setPersistenceEnabled(true);

        cfg.setConsistentId(igniteInstanceName);

        cfg.setClientMode(igniteInstanceName.startsWith(CLIENT_NODE_NAME_PREFIX));

        return cfg;
    }

    /** {@inheritDoc} */
    @Override protected void cleanPersistenceDir() throws Exception {
        super.cleanPersistenceDir();

        try (DirectoryStream<Path> files = newDirectoryStream(Paths.get(U.defaultWorkDirectory()), this::idleVerifyRes)) {
            for (Path path : files)
                delete(path);
        }
    }

    /**
     * Before command executed {@link #testOut} reset.
     *
     * @param args Arguments.
     * @return Result of execution.
     */
    protected int execute(String... args) {
        return execute(new ArrayList<>(asList(args)));
    }

    /**
     * Before command executed {@link #testOut} reset.
     *
     * @param args Arguments.
     * @return Result of execution
     */
    protected int execute(List<String> args) {
        return execute(new CommandHandler(createTestLogger()), args);
    }

    /**
     * Before command executed {@link #testOut} reset.
     *
     * @param hnd Handler.
     * @param args Arguments.
     * @return Result of execution
     */
    protected int execute(CommandHandler hnd, String... args) {
        return execute(hnd, new ArrayList<>(asList(args)));
    }

    /**
     * Before command executed {@link #testOut} reset.
     */
    protected int execute(CommandHandler hnd, List<String> args) {
        if (!F.isEmpty(args) && !"--help".equalsIgnoreCase(args.get(0)))
            addExtraArguments(args);

        testOut.reset();

        int exitCode = hnd.execute(args);

        // Flush all Logger handlers to make log data available to test.
        Logger logger = U.field(hnd, "logger");
        Arrays.stream(logger.getHandlers()).forEach(Handler::flush);

        return exitCode;
    }

    /**
     * Adds extra arguments required for tests.
     *
     * @param args Incoming arguments;
     */
    protected void addExtraArguments(List<String> args) {
        // Add force to avoid interactive confirmation.
        args.add(CMD_AUTO_CONFIRMATION);
    }

    /** */
    protected void injectTestSystemOut() {
        System.setOut(new PrintStream(testOut));
    }

    /**
     * Checks if all non-system txs and non-system mvcc futures are finished.
     */
    protected void checkUserFutures() {
        for (Ignite ignite : G.allGrids()) {
            IgniteEx ig = (IgniteEx)ignite;

            final Collection<GridCacheFuture<?>> futs = ig.context().cache().context().mvcc().activeFutures();

            boolean hasFutures = false;

            for (GridCacheFuture<?> fut : futs) {
                if (!fut.isDone()) {
                    //skipping system tx futures if possible
                    if (fut instanceof GridNearTxPrepareFutureAdapter
                        && ((GridNearTxPrepareFutureAdapter) fut).tx().system())
                        continue;

                    if (fut instanceof GridDhtTxPrepareFuture
                        && ((GridDhtTxPrepareFuture) fut).tx().system())
                        continue;

                    log.error("Expecting no active future [node=" + ig.localNode().id() + ", fut=" + fut + ']');

                    hasFutures = true;
                }
            }

            if (hasFutures)
                fail("Some mvcc futures are not finished");

            Collection<IgniteInternalTx> txs = ig.context().cache().context().tm().activeTransactions()
                .stream()
                .filter(tx -> !tx.system())
                .collect(Collectors.toSet());

            for (IgniteInternalTx tx : txs)
                log.error("Expecting no active transaction [node=" + ig.localNode().id() + ", tx=" + tx + ']');

            if (!txs.isEmpty())
                fail("Some transaction are not finished");
        }
    }

    /**
     * Creates default cache and preload some data entries.
     * <br/>
     * <table class="doctable">
     * <th>Cache parameter</th>
     * <th>Value</th>
     * <tr>
     *     <td>Name</td>
     *     <td>{@link #DEFAULT_CACHE_NAME}</td>
     * </tr>
     * <tr>
     *     <td>Affinity</td>
     *     <td>{@link RendezvousAffinityFunction} with exclNeighbors = false, parts = 32</td>
     * </tr>
     * <tr>
     *     <td>Number of backup</td>
     *     <td>1</td>
     * </tr>
     *
     * </table>
     *
     * @param ignite Ignite.
     * @param countEntries Count of entries.
     */
    protected void createCacheAndPreload(Ignite ignite, int countEntries) {
        assert nonNull(ignite);

        ignite.createCache(new CacheConfiguration<>(DEFAULT_CACHE_NAME)
            .setAffinity(new RendezvousAffinityFunction(false, 32))
            .setBackups(1));

        IgniteCache<Object, Object> cache = ignite.cache(DEFAULT_CACHE_NAME);
        for (int i = 0; i < countEntries; i++)
            cache.put(i, i);
    }
}
