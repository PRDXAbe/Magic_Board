# Magic Board

Real-time ball detection and counting system using an RPLIDAR A1-M8. A LIDAR sensor mounted at the edge of a physical drawing board scans across the surface and detects balls as they land, maintaining a running total count.

## Architecture

```mermaid
graph TD
    A[configure_board.py] -->|writes| B[board_config.json]
    B -->|loaded at startup| C[magic_board_live.py]
    C -->|subscribes| D[/scan ROS topic]
    D -->|LaserScan msgs| C
    C -->|renders| E[Matplotlib Live View]
    F[start_real_world.sh] -->|launches| G[sllidar_node]
    G -->|publishes| D
    F -->|launches| C
```

## Hardware

- **LIDAR**: RPLIDAR A1-M8 connected via USB (`/dev/ttyUSB0`)
- **Board**: 90 cm × 41 cm drawing board
- **Mounting**: LIDAR centered on one 41 cm edge, scanning into the board surface

## Requirements

- ROS 2 Humble
- Python 3 with `matplotlib` and `numpy`
- User must be in the `dialout` group for USB serial access

```bash
sudo usermod -aG dialout $USER   # re-login after this
```

## Build

Build the RPLIDAR driver (only needed once):

```bash
cd rw
source /opt/ros/humble/setup.bash
colcon build --packages-select sllidar_ros2
```

## Run

```bash
bash start_real_world.sh
```

This will:
1. Kill any stale LIDAR processes from previous runs
2. Start the RPLIDAR A1-M8 driver on `/dev/ttyUSB0`
3. Open the live top-down visualization with ball counter

Close the visualization window to stop everything.

## Configuration

### Changing Board Dimensions (Recommended)

Run the interactive configurator — **no code editing required**:

```bash
python3 configure_board.py
```

The tool lets you:
- View current dimensions in a coloured table (with cm equivalents)
- See an ASCII diagram of the board layout
- Edit any parameter interactively with live validation
- Save the result to `board_config.json`

`magic_board_live.py` reads `board_config.json` every time it starts, so the changes take effect on the next run.

### Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `board_min_x` | 0.050 m | Near edge — avoids LIDAR body |
| `board_max_x` | 0.860 m | Far edge |
| `board_min_y` | -0.190 m | Left edge |
| `board_max_y` | 0.190 m | Right edge |
| `cluster_dist` | 0.08 m | Max point gap within a ball cluster |
| `min_pts` | 2 pts | Minimum cluster size to count as a ball |
| `match_radius` | 0.20 m | Max centroid movement between frames |
| `forget_frames` | 25 fr | Frames before a track is dropped |
| `recount_frames` | 8 fr | Absent frames before reappearance counts as new ball |

### Manual Edit

You can also edit `board_config.json` directly with any text editor.

## Project Structure

```
Magic_Board/
├── start_real_world.sh       # Entry point — launches driver + visualizer
├── magic_board_live.py       # Live visualizer + ball counter
├── configure_board.py        # ← Interactive board measurement tool
├── board_config.json         # ← Board dimensions + detection parameters
├── rw/                       # Real-world ROS 2 workspace
│   └── src/
│       ├── sllidar_ros2-main/  # RPLIDAR A1/A2/A3 driver
│       └── ldlidar_stl_ros2/   # LD19/LD06 driver (alternative)
└── big_boulder/              # Simulation workspace + Kotlin UI
    └── src/
        └── adapt_display/    # Ball detection node (scan_tracker) + launch files
```

## Simulation Mode

A Gazebo-based simulation and Kotlin desktop UI are available for development without physical hardware:

```bash
# Terminal 1
cd big_boulder
source /opt/ros/humble/setup.bash
colcon build --packages-select adapt_display
source install/setup.bash
ros2 launch adapt_display launch_simulation.launch.py

# Terminal 2
cd big_boulder/AdaptBoard
./gradlew run
```
