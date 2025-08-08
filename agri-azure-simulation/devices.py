# devices.py ---------------------------------------------------------
# Creates one simulation module, 4 IoT Edge gateways and
# all child devices (environment sensors + irrigation actuators).

from azure.iot.hub import IoTHubRegistryManager


SERVICE_CONNECTION_STRING = "HostName=agriculture.azure-devices.net;SharedAccessKeyName=iothubowner;SharedAccessKey=S6+5v18VfGue98LN99lbkvYdnOvQEuxTbAIoTPRZSNY="


NUM_FARMS           = 2
GATEWAYS_PER_FARM   = 2
SENSORS_PER_GATEWAY = 10        # sensor-<farm>-<gw>-<idx>

registry = IoTHubRegistryManager(SERVICE_CONNECTION_STRING)

def create_device(device_id, edge=False):
    try:
        registry.get_device(device_id)
        print(f"{device_id} exists.")
    except Exception:
        registry.create_device_with_sas(device_id, None, None, "enabled")
        print(f"{device_id} created.")

    if edge:
        twin_patch = { "properties": { "desired": { "capabilities": { "iotEdge": True }}}}
        registry.update_twin(device_id, twin_patch, "*")

def tag_parent(child_id, parent_id):
    twin_patch = { "tags": { "parent": parent_id }}
    registry.update_twin(child_id, twin_patch, "*")

# 1) simulation module (runs on your laptop or a VM)
create_device("simulateModule"); tag_parent("simulateModule", "none")

# 2) gateways and hierarchy
for f in range(NUM_FARMS):
    for g in range(GATEWAYS_PER_FARM):
        gw_id = f"gw-{f}-{g}"
        create_device(gw_id, edge=True)

        # sensors
        for idx in range(SENSORS_PER_GATEWAY):
            s_id = f"sensor-{f}-{g}-{idx}"
            create_device(s_id); tag_parent(s_id, gw_id)

        # irrigation actuator
        act_id = f"irrig-{f}-{g}"
        create_device(act_id); tag_parent(act_id, gw_id)
