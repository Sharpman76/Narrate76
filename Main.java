/* Narrate76 by Sharpman76, v0.0.0

Documentation and a compiled program can be found here:
https://docs.google.com/document/d/e/2PACX-1vQt5JkAlRV-0VwwTAaGLZkLwp7On3MNHThR4vZ23J8rXKvN4JzXaKi1U6aSOamUQgOfovXt9gSTRWG0/pub

Narrate76 is a Java program that takes in a raw script for the dialogue/narration of a Minecraft map
and generates a datapack and resource pack to display subtitles and play sound files for each line of
dialogue at the mapmakerâ€™s command. The program is intended for use in an adventure-map-style scenario
where the lines of dialogue are in a fixed order and only get displayed/voiced one time each per playthrough.

If you want to borrow the code and make modifications to suit your own purposes, feel free, but it
would be nice if you could credit me if you include Narrate76-derived dialogue in any projects.

If the code starts feeling homesick, try running it in IntelliJ light mode */

// Got a trade deficit higher than the United States with this many imports
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Main{

    public static void main(String[] args) {

        // Lists of data ordered by speaker
        ArrayList<String> speakerList = new ArrayList<String>();
        ArrayList<String> colorList = new ArrayList<String>();
        ArrayList<String>[] orderedLinesList = new ArrayList[0];

        // Lists of data in order of appearance in the script
        ArrayList<String> speakers = new ArrayList<String>();
        ArrayList<String> lines = new ArrayList<String>();
        ArrayList<String> times = new ArrayList<String>();
        ArrayList<String> colors = new ArrayList<String>();
        ArrayList<String> numbers = new ArrayList<String>();

        // Try to parse script.txt, which contains all input from the user
        try {
            Scanner script = new Scanner(new File("script.txt"));
            String nextRawLine = nextEscapedLine(script);
            // Interpret the incoming lines of text as speakers up until the >>> separator
            while (!nextRawLine.equals(">>>")) {
                speakerList.add(nextRawLine);
                colorList.add(nextEscapedLine(script));
                script.nextLine();
                nextRawLine = nextEscapedLine(script);
            }
            // Check that no duplicate speakers exist
            for (String speaker1 : speakerList) {
                for (String speaker2 : speakerList) {
                    if (speaker1 != speaker2 && simp(speaker1).equals(simp(speaker2))) {
                        error("Speaker \"" + speaker1 + "\" is defined more than once, " +
                                "or it only differs from another speaker by certain special symbols: \\/:*?\"<>|");
                    }
                }
            }
            // Interpret the incoming lines of text as dialogue once past the >>> separator
            orderedLinesList = new ArrayList[speakerList.size()];
            for (int i=0; i<speakerList.size(); i++) {
                orderedLinesList[i] = new ArrayList<String>();
            }
            while (script.hasNextLine()) {
                script.nextLine();
                String thisSpeaker = nextEscapedLine(script);
                String thisLine = nextEscapedLine(script);
                boolean foundSpeaker = false;
                for (int i=0; i<speakerList.size(); i++) {
                    if (thisSpeaker.equals(speakerList.get(i))) {
                        foundSpeaker = true;
                        numbers.add(String.valueOf(orderedLinesList[i].size()));
                        orderedLinesList[i].add(thisLine);
                        colors.add(colorList.get(i));
                        break;
                    }
                }
                if (!foundSpeaker) {
                    error("Speaker \"" + thisSpeaker + "\" is not defined.");
                }
                speakers.add(thisSpeaker);
                lines.add(thisLine);
                times.add(nextEscapedLine(script));
            }
            script.close();
        } catch (FileNotFoundException e) {
            writeFile("script.txt", "Speaker\ngreen\n\n>>>\n\nSpeaker\nThis is a line of dialogue!\n-1");
            error("Could not locate script.txt file; a sample has been provided.",e);
        }

        // Assemble the contents of reset.mcfunction, which stores all the JSON data of the dialogue in a data storage
        StringBuilder reset = new StringBuilder("#declare storage narrate76:dialogue\n" +
                "data modify storage narrate76:dialogue Lines set value [");
        for (int i = lines.size()-1; i >= 0; i--){
            String lineName = simp(speakers.get(i)) + ".ln" + (Integer.valueOf(numbers.get(i))+1);
            reset.append("{Text:\'[" + (colors.get(i).equals("hide") ? "" : "\"<\",{\"translate\":\"narrate76." +
                    simp(speakers.get(i)) + "\",\"color\":\"" + colors.get(i) + "\"},\"> \",") + "{\"translate\":\"narrate76." +
                    lineName + "\"}]',Time:" + (int)(Double.valueOf(times.get(i))*20) + ",Voice:\"execute at @a run playsound narrate76:" +
                    lineName + " voice @p\"}");
            if (i != 0) {
                reset.append(",");
            }
        }
        reset.append("]\n" +
                "scoreboard players set Time Narrate76.Clock -1\n" +
                "scoreboard players set LinesRead Narrate76.Clock 0");


        // Assemble the contents of en_us.json, which matches all the translate keys for the dialogue lines to the actual text they represent
        StringBuilder en_us = new StringBuilder("{");
        for (int i = 0; i<speakerList.size(); i++) {
            en_us.append("\n    \"narrate76." + simp(speakerList.get(i)) + "\": \"" + speakerList.get(i) + "\",\n");
            for (int j = 0; j<orderedLinesList[i].size(); j++) {
                en_us.append("    \"narrate76." + simp(speakerList.get(i)) + ".ln" + String.valueOf(j+1) +
                        "\": \"" + orderedLinesList[i].get(j) + "\",\n");
            }
        }
        en_us.append("\n    \"narrate76.your.resource.pack.is.broken\": \"Your resource pack is functioning correctly!\"\n}");

        // Assemble the contents of sounds.json, which matches all sound files to the names they represent in /playsound
        StringBuilder sounds = new StringBuilder("{");
        for (int i = 0; i<speakerList.size(); i++) {
            sounds.append("\n");
            for (int j = 0; j<orderedLinesList[i].size(); j++) {
                String lineName = simp(speakerList.get(i)) + ".ln" + String.valueOf(j+1);
                sounds.append("\t\"" + lineName + "\": {\n\t\t\"sounds\": [\n\t\t\t{\n\t\t\t\t\"name\": \"narrate76:" +
                        lineName + "\"\n\t\t\t}\n\t\t]\n\t},\n");
            }
        }
        sounds.replace(sounds.length()-2,sounds.length(),"").append("\n}");

        // Create directories for the datapack and resource pack
        String pathFunctions = createFolder("datapacks/Narrate76/data/narrate76/functions");
        String pathFunctionTags = createFolder("datapacks/Narrate76/data/minecraft/tags/functions");
        String pathLang = createFolder("resources/assets/narrate76/lang");
        String pathSounds = createFolder("resources/assets/narrate76/sounds");
        // pack.mcmeta is the same for the datapack and resource pack
        String packDotMcmeta = "{\n" +
                "    \"pack\": {\n" +
                "        \"pack_format\": 6,\n" +
                "        \"description\": \"A dialogue/narration system which iterates through lines on command\"\n" +
                "    }\n" +
                "}";

        // Write files for the datapack
        writeFile("datapacks/Narrate76/pack.mcmeta", packDotMcmeta);
        writeFile(pathFunctionTags + "/load.json",
                "{\n" +
                "    \"values\": [\"narrate76:load\"]\n" +
                "}\n");
        writeFile(pathFunctionTags + "/tick.json",
                "{\n" +
                        "    \"values\": [\"narrate76:tick\"]\n" +
                        "}\n");
        writeFile(pathFunctions + "/load.mcfunction",
                "scoreboard objectives add Narrate76.Clock dummy\n" +
                "forceload add 0 0\n" +
                "setblock 9 0 9 repeating_command_block{auto:1}\n" +
                "tellraw @a [\"\",{\"text\":\"Loaded Narrate76 Datapack v0.0.0 by Sharpman76\\n\",\"color\":\"green\"}," +
                        "{\"translate\":\"narrate76.your.resource.pack.is.broken\"}]");
        writeFile(pathFunctions + "/tick.mcfunction",
                "data modify block 9 0 9 Command set value \"\"\n" +
                "execute if score Time Narrate76.Clock matches 1.. run scoreboard players remove Time Narrate76.Clock 1\n" +
                "execute if score Time Narrate76.Clock matches 0 run function narrate76:next");
        writeFile(pathFunctions + "/next.mcfunction",
                "tellraw @a {\"nbt\":\"Lines[-1].Text\",\"storage\":\"narrate76:dialogue\",\"interpret\":true}\n" +
                "execute store result score Time Narrate76.Clock run data get storage narrate76:dialogue Lines[-1].Time\n" +
                "data modify block 9 0 9 Command set from storage narrate76:dialogue Lines[-1].Voice\n" +
                "scoreboard players add LinesRead Narrate76.Clock 1\n" +
                "data remove storage narrate76:dialogue Lines[-1]");
        writeFile(pathFunctions + "/reset.mcfunction", reset.toString());

        // Write files for the resource pack
        writeFile("resources/pack.mcmeta", packDotMcmeta);
        writeFile(pathLang + "/en_us.json", en_us.toString());
        writeFile(pathSounds + ".json", sounds.toString());

        // Compress the resources folder into a zip so the game can recognize it
        try {
            (new File("resources.zip")).delete();
            pack("resources","resources.zip");
        } catch (IOException e) {
            error("An error occurred while zipping files; make sure your world is not open in Minecraft, " +
                    "as this will prevent the program from deleting old resources.zip files.",e);
        }

        // Delete any error message file present if no problems were detected
        (new File("narrate76 error message.txt")).delete();
        System.out.println("Datapack and resource pack were successfully generated!");
    }

    // Simplifies speaker names for storage and comparison
    private static String simp(String name) {
        return name.replace("Â","").replace("§","&").replace("\\","").replace("/","").replace(":","").replace("*","").replace("?","")
                .replace("\"","").replace(">","").replace("<","").replace("|","").replace(" ","_").toLowerCase();
    }

    // Sanitizes inputs from script.txt
    private static String nextEscapedLine(Scanner scanner) {
        return scanner.nextLine().replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // Creates an error message in a text file because there's no console to print to
    private static void error(String errorMessage, Exception e) {
        error(errorMessage + "\n\nError message: " + e.getMessage());
    }
    private static void error(String errorMessage) {
        System.out.println(errorMessage);
        writeFile("narrate76 error message.txt", errorMessage);
        System.exit(1);
    }

    // The explanations of these functions are left as an exercise to the reader
    private static void writeFile(String path, String contents) {
        try {
            FileWriter writeFile = new FileWriter(path);
            writeFile.write(contents);
            writeFile.close();
        } catch (IOException e) {
            error("An error occurred while writing file " + path,e);
        }

    }
    private static String createFolder(String path) {
        (new File(path)).mkdirs();
        return path;
    }

    // Compresses final resources folder, credit goes to Nikita Koksharov and jjst on StackOverflow:
    // https://stackoverflow.com/questions/15968883/how-to-zip-a-folder-itself-using-java
    public static void pack(String sourceDirPath, String zipFilePath) throws IOException {
        Path p = Files.createFile(Paths.get(zipFilePath));
        try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(p))) {
            Path pp = Paths.get(sourceDirPath);
            Files.walk(pp)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        ZipEntry zipEntry = new ZipEntry(pp.relativize(path).toString().replace("\\","/"));
                        try {
                            zs.putNextEntry(zipEntry);
                            Files.copy(path, zs);
                            zs.closeEntry();
                        } catch (IOException e) {
                            error("An error occurred before zipping files.",e);
                        }
                    });
        }
    }
}