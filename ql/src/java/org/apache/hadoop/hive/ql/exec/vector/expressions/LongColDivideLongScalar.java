/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hive.ql.exec.vector.expressions;

import java.util.Arrays;

import org.apache.hadoop.hive.ql.exec.vector.DoubleColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.LongColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorExpressionDescriptor;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;

/**
 * This operation is handled as a special case because Hive
 * long/long division returns double. This file is thus not generated
 * from a template like the other arithmetic operations are.
 */
public class LongColDivideLongScalar extends VectorExpression {
  private static final long serialVersionUID = 1L;
  private int colNum;
  private long value;
  private int outputColumn;

  public LongColDivideLongScalar(int colNum, long value, int outputColumn) {
    this();
    this.colNum = colNum;
    this.value = value;
    this.outputColumn = outputColumn;
  }

  public LongColDivideLongScalar() {
    super();
  }

  @Override
  public void evaluate(VectorizedRowBatch batch) {

    if (childExpressions != null) {
      super.evaluateChildren(batch);
    }

    LongColumnVector inputColVector = (LongColumnVector) batch.cols[colNum];
    DoubleColumnVector outputColVector = (DoubleColumnVector) batch.cols[outputColumn];
    int[] sel = batch.selected;
    boolean[] inputIsNull = inputColVector.isNull;
    boolean[] outputIsNull = outputColVector.isNull;
    int n = batch.size;
    long[] vector = inputColVector.vector;
    double[] outputVector = outputColVector.vector;

    // return immediately if batch is empty
    if (n == 0) {
      return;
    }

    // We do not need to do a column reset since we are carefully changing the output.
    outputColVector.isRepeating = false;

    if (value == 0) {
      // Denominator is zero, convert the batch to nulls
      outputColVector.noNulls = false;
      outputColVector.isRepeating = true;
      outputIsNull[0] = true;
      NullUtil.setNullOutputEntriesColScalar(outputColVector, batch.selectedInUse, sel, n);
      return;
    } else if (inputColVector.isRepeating) {
      if (inputColVector.noNulls || !inputIsNull[0]) {
        outputIsNull[0] = false;
        outputVector[0] = vector[0] / (double) value;
      } else {
        outputIsNull[0] = true;
        outputColVector.noNulls = false;
      }
      outputColVector.isRepeating = true;
      NullUtil.setNullOutputEntriesColScalar(outputColVector, batch.selectedInUse, sel, n);
      return;
    }

    if (inputColVector.noNulls) {
      if (batch.selectedInUse) {

        for(int j = 0; j != n; j++) {
          final int i = sel[j];
          // Set isNull before call in case it changes it mind.
          outputIsNull[i] = false;
          outputVector[i] = vector[i] / (double) value;
        }
      } else {
        // Assume it is almost always a performance win to fill all of isNull so we can
        // safely reset noNulls.
        Arrays.fill(outputIsNull, false);
        outputColVector.noNulls = true;
        for(int i = 0; i != n; i++) {
          outputVector[i] = vector[i] / (double) value;
        }
      }
    } else /* there are nulls */ {

      // Carefully handle NULLs...

      /*
       * For better performance on LONG/DOUBLE we don't want the conditional
       * statements inside the for loop.
       */
      outputColVector.noNulls = false;

      if (batch.selectedInUse) {
        for(int j = 0; j != n; j++) {
          int i = sel[j];
          outputVector[i] = vector[i] / (double) value;
          outputIsNull[i] = inputIsNull[i];
        }
      } else {
        for(int i = 0; i != n; i++) {
          outputVector[i] = vector[i] / (double) value;
        }
        System.arraycopy(inputIsNull, 0, outputIsNull, 0, n);
      }
    }

    /* Set double data vector array entries for NULL elements to the correct value.
     * Unlike other col-scalar operations, this one doesn't benefit from carrying
     * over NaN values from the input array.
     */
    NullUtil.setNullDataEntriesDouble(outputColVector, batch.selectedInUse, sel, n);
  }

  @Override
  public int getOutputColumn() {
    return outputColumn;
  }

  @Override
  public String getOutputType() {
    return "double";
  }

  public int getColNum() {
    return colNum;
  }

  public void setColNum(int colNum) {
    this.colNum = colNum;
  }

  public long getValue() {
    return value;
  }

  public void setValue(long value) {
    this.value = value;
  }

  public void setOutputColumn(int outputColumn) {
    this.outputColumn = outputColumn;
  }

  @Override
  public VectorExpressionDescriptor.Descriptor getDescriptor() {
    return (new VectorExpressionDescriptor.Builder())
        .setMode(
            VectorExpressionDescriptor.Mode.PROJECTION)
        .setNumArguments(2)
        .setArgumentTypes(
            VectorExpressionDescriptor.ArgumentType.INT_FAMILY,
            VectorExpressionDescriptor.ArgumentType.INT_FAMILY)
        .setInputExpressionTypes(
            VectorExpressionDescriptor.InputExpressionType.COLUMN,
            VectorExpressionDescriptor.InputExpressionType.SCALAR).build();
  }
}
