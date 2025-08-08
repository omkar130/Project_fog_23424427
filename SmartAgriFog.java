package org.fog.test.perfeval;

import java.util.*;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.*;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.*;
import org.fog.placement.*;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.*;
import org.fog.utils.distribution.DeterministicDistribution;

public class SmartAgriFog {

    /* ---------- topology / workload parameters ---------- */
    private static final int NUM_FARMS = 2;                // number of farms
    private static final int GATEWAYS_PER_FARM = 2;        // LoRa gateways per farm
    private static final int SENSORS_PER_GATEWAY = 10;     // env sensors per gateway
    private static final double SENSING_INTERVAL = 5.0;    // seconds between samples

    /* Edge-ward or Cloud-only?  true = cloud-only baseline, false = edge/fog */
    private static final boolean CLOUD = false;

    /* ---------- iFogSim bookkeeping ---------- */
    private static final List<FogDevice> fogDevices = new ArrayList<>();
    private static final List<Sensor>    sensors    = new ArrayList<>();
    private static final List<Actuator>  actuators  = new ArrayList<>();

    public static void main(String[] args) {

        Log.printLine("Starting Smart-Agriculture Simulation...");

        try {
            Log.disable();                           // keep the console tidy
            CloudSim.init(1, Calendar.getInstance(), false);

            String appId = "smart_agri";
            FogBroker broker = new FogBroker("broker");

            Application application = createApplication(appId, broker.getId());
            application.setUserId(broker.getId());

            createFogDevices(broker.getId(), appId);

            /* ---------- module placement rules ---------- */
            ModuleMapping mapping = ModuleMapping.createModuleMapping();

            // Storage is always in the cloud
            mapping.addModuleToDevice("storage", "cloud");

            if (CLOUD) {                // cloud-only baseline
                mapping.addModuleToDevice("preprocessing", "cloud");
                mapping.addModuleToDevice("analytics",     "cloud");
            } else {                    // edge-ward placement
                for (FogDevice d : fogDevices)
                    if (d.getName().startsWith("gw-"))     // LoRa gateways
                        mapping.addModuleToDevice("preprocessing", d.getName());
                // analytics will be dynamically placed on farm fog servers by policy
            }

            Controller controller = new Controller("master", fogDevices, sensors, actuators);
            controller.submitApplication(
                    application, 0,
                    CLOUD
                      ? new ModulePlacementMapping(fogDevices, application, mapping)
                      : new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, mapping)
            );

            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            Log.printLine("Smart-Agriculture Simulation finished!");

        } catch (Throwable t) {
            t.printStackTrace();
            Log.printLine("Unexpected error!");
        }
    }

    /* ---------- physical topology ---------- */
    private static void createFogDevices(int userId, String appId) {

        // Cloud datacenter (level 0)
        FogDevice cloud = createFogDevice("cloud", 44800, 40000, 10000, 10000,
                                          0, 0.01, 16 * 103, 16 * 83.25);
        cloud.setParentId(-1);
        fogDevices.add(cloud);

        // For each farm: one farm-level fog server (level 1) + gateways (level 2)
        for (int f = 0; f < NUM_FARMS; f++) {

            FogDevice farmFog = createFogDevice("fog-farm-" + f, 6000, 8000,
                                                10000, 10000, 1, 0.0,
                                                107.339, 83.4333);
            farmFog.setParentId(cloud.getId());
            farmFog.setUplinkLatency(80);            // WAN+MAN latency to cloud
            fogDevices.add(farmFog);

            /* LoRa gateways and attached sensors/actuators */
            for (int g = 0; g < GATEWAYS_PER_FARM; g++) {

                String gwId = "gw-" + f + "-" + g;
                FogDevice gateway = createFogDevice(gwId, 1200, 1000,
                                                    10000, 10000, 2, 0.0,
                                                    87.53, 82.44);
                gateway.setParentId(farmFog.getId());
                gateway.setUplinkLatency(5);        // LoRa backhaul to fog server
                fogDevices.add(gateway);

                /* attach sensors & actuators to this gateway */
                for (int s = 0; s < SENSORS_PER_GATEWAY; s++) {
                    String sensorId = "s-" + gwId + "-" + s;
                    Sensor envSensor = new Sensor(sensorId, "ENV_SENSOR",
                            userId, appId, new DeterministicDistribution(SENSING_INTERVAL));
                    envSensor.setGatewayDeviceId(gateway.getId());
                    envSensor.setLatency(1.0);      // sensor → gateway latency
                    sensors.add(envSensor);
                }

                Actuator irrig = new Actuator("irrig-" + gwId, userId, appId, "IRRIGATION_CTRL");
                irrig.setGatewayDeviceId(gateway.getId());
                irrig.setLatency(0.5);
                actuators.add(irrig);
            }
        }
    }

    /* ---------- helper: generic fog-device factory ---------- */
    private static FogDevice createFogDevice(String name, long mips, int ram,
                                             long upBw, long downBw, int level,
                                             double ratePerMips,
                                             double busyPower, double idlePower) {

        List<Pe> pes = List.of(new Pe(0, new PeProvisionerOverbooking(mips)));
        int hostId = FogUtils.generateEntityId();
        PowerHost host = new PowerHost(
                hostId, new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(10000), 1000000,
                pes, new StreamOperatorScheduler(pes),
                new FogLinearPowerModel(busyPower, idlePower));

        List<Host> hostList = List.of(host);
        FogDeviceCharacteristics ch = new FogDeviceCharacteristics(
                "x86", "Linux", "Xen", host, 10.0,
                3.0, 0.05, 0.001, 0.0);

        try {
            FogDevice dev = new FogDevice(name, ch,
                    new AppModuleAllocationPolicy(hostList),
                    new LinkedList<>(), 10, upBw, downBw, 0, ratePerMips);
            dev.setLevel(level);
            return dev;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Builds the smart-agriculture application DAG.
     * Sensors → preprocessing → analytics → (actuator | cloud storage)
     */
    @SuppressWarnings("serial")
    private static Application createApplication(String appId, int userId) {

        Application app = Application.createApplication(appId, userId);

        /* ---------- modules ---------- */
        app.addAppModule("preprocessing", 10);   // edge (gateways)
        app.addAppModule("analytics",     10);   // fog (farm server)
        app.addAppModule("storage",       10);   // cloud

        /* ---------- edges ---------- */
        // LoRa sensor → preprocessing on gateway
        app.addAppEdge("ENV_SENSOR", "preprocessing",
                500, 2000, "ENV_SENSOR",          // tupleType matches sensorType
                Tuple.UP, AppEdge.SENSOR);

        // Gateway preprocessing → farm-fog analytics
        app.addAppEdge("preprocessing", "analytics",
                800, 1000, "FILTERED_DATA",
                Tuple.UP, AppEdge.MODULE);

        // Analytics → irrigation actuator (edge of gateway)
        app.addAppEdge("analytics", "IRRIGATION_CTRL",
                100, 50, 1000, "IRR_CMD",
                Tuple.DOWN, AppEdge.ACTUATOR);

        // Analytics → cloud storage for long-term data
        app.addAppEdge("analytics", "storage",
                200, 4000, "STORE_DATA",
                Tuple.UP, AppEdge.MODULE);

        /* ---------- tuple mappings ---------- */
        // Pass every sensor reading through preprocessing
        app.addTupleMapping("preprocessing", "ENV_SENSOR", "FILTERED_DATA",
                new FractionalSelectivity(1.0));

        // Every filtered tuple: 100 % controls irrigation + 100 % logged to cloud
        app.addTupleMapping("analytics", "FILTERED_DATA", "IRR_CMD",
                new FractionalSelectivity(1.0));        // set < 1.0 for sparse actuation
        app.addTupleMapping("analytics", "FILTERED_DATA", "STORE_DATA",
                new FractionalSelectivity(1.0));

        /* ---------- latency-critical loop ---------- */
        AppLoop controlLoop = new AppLoop(new ArrayList<String>() {{
            add("ENV_SENSOR");
            add("preprocessing");
            add("analytics");
            add("IRRIGATION_CTRL");
        }});
        app.setLoops(Collections.singletonList(controlLoop));

        return app;
    }
}
