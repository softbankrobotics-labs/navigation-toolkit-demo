# Navigation Toolkit Demo

![Screenshot of the main menu](./screenshot.png)

This Android app presents an implementation of various [SoftBank Robotics Labs libraries](https://github.com/softbankrobotics-labs/):
- [Pepper Conversation Menu](https://github.com/softbankrobotics-labs/pepper-conversation-menu), used for all the menus in the app. It makes it possible to navigate in the menus by touching the tablet or by using the robot's `Chat`. For instance, to select the "Come Here" option on the main screen, say "Come here" to the robot.
- [Pepper Extras](https://github.com/softbankrobotics-labs/pepper-extras), used to run `StubbornGoTo` actions. The `StubbornGoTo` action is like the `GoTo` action, but has a better success rate as it will retry with differents strategies if it fails.
- [Pepper Follow Me](https://github.com/softbankrobotics-labs/pepper-follow-me), used to make the robot follow someone when asked to do so.
- [Pepper Gamepad](https://github.com/softbankrobotics-labs/pepper-gamepad), used to control the robot with a gamepad.
- [Pepper Point At](https://github.com/softbankrobotics-labs/pepper-point-at), used to make the robot show where its Home Frame is when asked to do so.

## Features

- Come Here: if Pepper has detected a human, it come near him and stop.
- Follow Me: if Pepper has detected a human, it follow it until instructed to stop.
- Gamepad: if a gamepad is connected to the tablet, instructions are displayed to explain how to make Pepper move. If a gamepad isn't connected, explanations are given on how to connect a gamepad to the tablet.
- Mapping: this feature allows Pepper to look at its environment to build an [ExplorationMap](https://developer.softbankrobotics.com/pepper-qisdk/api/motion/reference/explorationmap#exploration-map). This allows Pepper to make more precise movements. It is also possible to use the gamepad to make Pepper move during the mapping.
- Go Home: Pepper go to its Home Frame. The Home Frame is the center of the `ExplorationMap` Pepper has in memory.
- Go To Pod: this feature launches an Activity from the [Autonomous Recharge Companion Library](https://github.com/aldebaran/qisdk-sample-autonomous-recharge-advanced-integration) to make Pepper go to its pod.
- Point At Home: Pepper makes an animation to show where its Home Frame is.

## License

This project is licensed under the BSD 3-Clause "New" or "Revised" License. See the [LICENSE](LICENSE.md) file for details.