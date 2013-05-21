package de.jungblut.classification.knn;

import junit.framework.TestCase;

import org.junit.Test;

import de.jungblut.datastructure.ArrayUtils;
import de.jungblut.math.DoubleVector;
import de.jungblut.math.dense.DenseDoubleVector;
import de.jungblut.math.tuple.Tuple;
import de.jungblut.reader.MushroomReader;

public class KNearestNeighboursTest extends TestCase {

  private static final int TEST_SET_SIZE = 100;

  @Test
  public void testKNN() throws Exception {
    Tuple<DoubleVector[], DenseDoubleVector[]> mushroom = MushroomReader
        .readMushroomDataset("files/mushroom/mushroom_dataset.csv");
    DoubleVector[] fullFeatures = mushroom.getFirst();
    DenseDoubleVector[] fullOutcome = mushroom.getSecond();

    DoubleVector[] testFeatures = ArrayUtils.subArray(fullFeatures,
        fullFeatures.length - TEST_SET_SIZE, fullFeatures.length - 1);

    DoubleVector[] testOutcome = ArrayUtils.subArray(fullOutcome,
        fullOutcome.length - TEST_SET_SIZE, fullOutcome.length - 1);

    DoubleVector[] trainFeatures = ArrayUtils.subArray(fullFeatures,
        fullFeatures.length - TEST_SET_SIZE);

    DenseDoubleVector[] trainOutcome = ArrayUtils.subArray(fullOutcome,
        fullOutcome.length - TEST_SET_SIZE);

    KNearestNeighbours knn = new KNearestNeighbours(2, 10);
    knn.train(trainFeatures, trainOutcome);

    int correct = 0;
    for (int i = 0; i < testFeatures.length; i++) {
      DoubleVector predict = knn.predict(testFeatures[i]);
      if (predict.get(0) == ((int) testOutcome[i].get(0))) {
        correct++;
      }
    }
    // assert that everything is correct
    assertEquals(100, correct);

  }
}
