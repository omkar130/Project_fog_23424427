# plot_distribution.py  ── Smart-Agri edition
import pandas as pd
import matplotlib.pyplot as plt

CSV         = "smartagri_telemetry.csv"
SENSOR_TYPE = "SOIL_MOISTURE"        # choose any of the four types

# load and filter
df = pd.read_csv(CSV, parse_dates=["timestamp"])
vals = df[df["sensorType"] == SENSOR_TYPE]["value"]

# plot
plt.figure(figsize=(8, 5))
plt.hist(vals, bins=12, edgecolor="black")
plt.title(f"Distribution of {SENSOR_TYPE} readings")
plt.xlabel("Value")
plt.ylabel("Frequency")
plt.tight_layout()
plt.savefig(f"{SENSOR_TYPE.lower()}_distribution.png")
plt.show()
