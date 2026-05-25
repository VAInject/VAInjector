# VAInjector

## About

VAInjector is a Mod Injector for Minecraft's oldest version **rd-132211**

## How to make mods for VAInjector

To make mods you need to first decompile rd-132211, you can do so by installing [Vineflower](https://github.com/Vineflower/vineflower/releases) and following their [Usage Doc](https://vineflower.org/usage/)

Once decompiled, locate the class you wish to modify within the source tree. Copy its full contents into your own project under the identical package and class name, then alter its logic as desired. Compile the modified class and package it into a JAR file, that is all that is required. Place the resulting JAR into the `mods/` folder and you are done!

Or just follow [KiloByteNight's Youtube Video.](https://www.youtube.com/watch?v=MHUKCskoooI)


## How to use VAInjector?

To use VAInjector or to download mods and play them you first need to download the installer jar from the [releases tab.](https://github.com/VAInject/VAInjector)

Once you have downloaded the installer jar place it anywhere and run the installer.

Make sure to select where your rd-132211 instance is (use prismlauncher cause its the only launcher it currently supports)

Double check if the installer is set to `\instance-name\` and not `\instance-name\minecraft\`.

When its finished installing (very fast) put your mods inside the mods directory and run `launch.bat` if you are on windows and `launch.sh` if you are on linux.

That is it! You can now play with mods easily on Minecraft's oldest version ever.
