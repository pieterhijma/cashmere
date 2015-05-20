import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

import java.util.Arrays;

public class ConvertToBinary {

    private static int nFeatures;

    private static final float[] parseVector(String line) {
	String[] numbers = line.split(" ");
        if (nFeatures == 0) {
            nFeatures = numbers.length;
        } else if (nFeatures != numbers.length) {
            throw new Error("Error in input: inconsistent number of features");
        }
	float[] output = new float[numbers.length];
	for (int i = 0; i < output.length; ++i) {
	    output[i] = Float.valueOf(numbers[i]);
	}
	return output;
    }

    // Read initial "centers" file.
    private static final float[] readCenters(String file) throws Exception {
	BufferedReader buffer = new BufferedReader(new FileReader(file));
        int index = 0;
        float[] result = new float[65536];
	String line;
	while ((line = buffer.readLine()) != null) {
	    float[] v = parseVector(line);
            if (index + v.length > result.length) {
                float[] newResult = new float[2*result.length];
                System.arraycopy(result, 0, newResult, 0, index);
                result = newResult;
            }
            for (float f : v) {
                result[index] = f;
                index++;
            }
	}
	buffer.close();
	return Arrays.copyOf(result, index);
    }

    // Read "points" directory.
    private static final float[] readPoints(String dir) throws Exception {
	File d = new File(dir);
	File[] files = d.listFiles();
	if (files != null) {
            float[] result = new float[65536];
            int index = 0;
	    for (File file : files) {
		BufferedReader buffer = new BufferedReader(new FileReader(file));
		String line;
		while ((line = buffer.readLine()) != null) {
                    float[] v = parseVector(line);
                    if (index + v.length > result.length) {
                        float[] newResult = new float[2*result.length];
                        System.arraycopy(result, 0, newResult, 0, index);
                        result = newResult;
                    }
                    for (float f : v) {
                        result[index] = f;
                        index++;
                    }
		}
		buffer.close();
	    }
            return Arrays.copyOf(result, index);
	} else {
	    throw new Exception("Could not open directory " + dir);
	}
    }

    public static void writeArray(File dir, float[] f, String name) throws Exception {

        ByteArrayOutputStream bs = new ByteArrayOutputStream();
        DataOutputStream ds = new DataOutputStream(bs);
        ds.writeInt(nFeatures);
        ds.writeInt(f.length);
        for (int i = 0; i < f.length; i++) {
            ds.writeFloat(f[i]);
        }
        ds.close();
        bs.writeTo(new FileOutputStream(new File(dir, name)));
    }

    public static void main(String[] args) throws Exception {

        String centerFile = null;
        String pointsDir = null;
        String destinationDir = null;

        if (args.length < 3) {
            throw new Error("Usage: java ConvertToBinary <centerFile> <pointsDir> <targetDir>");
        }
        centerFile = args[0];
        pointsDir = args[1];
        destinationDir = args[2];

        File dest = new File(destinationDir);

        if (dest.exists()) {
            if (! dest.isDirectory()) {
                throw new Error("Destination " + destinationDir + " exists and is not a directory");
            }
        } else {
            if (! dest.mkdirs()) {
                throw new Error("Could not create destination dir " + destinationDir);
            }
        }

	float[] centers = readCenters(centerFile);
        writeArray(dest, centers, "centers");


        float[] points = readPoints(pointsDir);
        writeArray(dest, points, "points");
    }
}
