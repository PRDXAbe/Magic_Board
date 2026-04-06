#!/usr/bin/env bash
# start_real_world.sh
# Usage:  bash start_real_world.sh
#
# Starts the RPLIDAR A1-M8 driver then opens the live visualization.
# Edit magic_board_live.py (top section) to change board dimensions.

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RW="$SCRIPT_DIR/rw"

echo "[magic_board] Sourcing workspaces..."
source "$RW/install/setup.bash"

# ── Kill any leftover LIDAR processes from previous runs ──────────────────────
echo "[magic_board] Cleaning up any leftover LIDAR processes..."
pkill -f sllidar_node  2>/dev/null && echo "  → killed stale sllidar_node" || true
pkill -f ldlidar       2>/dev/null && echo "  → killed stale ldlidar"       || true
pkill -f magic_board_live 2>/dev/null && echo "  → killed stale viz"        || true

# Give the OS a moment to release the serial port
sleep 1

echo "[magic_board] Starting RPLIDAR A1-M8 driver..."
ros2 run sllidar_ros2 sllidar_node \
    --ros-args \
    -p serial_port:=/dev/ttyUSB0 \
    -p serial_baudrate:=115200 \
    -p frame_id:=laser_frame \
    -p inverted:=false \
    -p angle_compensate:=true &

LIDAR_PID=$!
echo "[magic_board] LIDAR driver PID: $LIDAR_PID"

# Wait for driver to start and confirm it's alive
echo "[magic_board] Waiting for driver to connect..."
sleep 3

if ! kill -0 $LIDAR_PID 2>/dev/null; then
    echo "[magic_board] ERROR: LIDAR driver exited. Check that:"
    echo "  • RPLIDAR A1-M8 is plugged into USB"
    echo "  • Port is /dev/ttyUSB0  (check: ls /dev/ttyUSB*)"
    echo "  • You have permission  (check: groups | grep dialout)"
    exit 1
fi

echo "[magic_board] Driver is running — opening live visualization..."
python3 "$SCRIPT_DIR/magic_board_live.py"

# When the visualization window is closed, also stop the driver
echo "[magic_board] Visualization closed — stopping LIDAR driver..."
kill $LIDAR_PID 2>/dev/null || true
wait $LIDAR_PID 2>/dev/null || true
echo "[magic_board] Done."
