# plot_all.py  ── Smart-Agri edition
import pandas as pd
import matplotlib.pyplot as plt

CSV = "smartagri_telemetry.csv"

# load and resample to 1-minute means so the plot stays readable
df = pd.read_csv(CSV, parse_dates=["timestamp"])
df.set_index("timestamp", inplace=True)

plt.figure(figsize=(12, 6))
for s_type in sorted(df["sensorType"].unique()):
    series = (
        df[df["sensorType"] == s_type]
          .groupby("sensorType")["value"]        # keep “value” column
          .resample("1min")                      # 1-minute bins
          .mean()
          .droplevel(0)                          # drop sensorType index
    )
    plt.plot(series.index, series.values, marker="o", linestyle="-", label=s_type)

plt.title("Average sensor readings – all devices")
plt.xlabel("Time (1-min buckets)")
plt.ylabel("Value")
plt.legend()
plt.xticks(rotation=45)
plt.grid(True)
plt.tight_layout()
plt.savefig("all_sensors_timeseries.png")
plt.show()
