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

import org.apache.hadoop.hive.ql.exec.vector.BytesColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorExpressionDescriptor;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.hadoop.io.Text;

/**
 * Expression for vectorized evaluation of unary UDFs on strings.
 * An object of {@link IUDFUnaryString} is applied to every element of
 * the vector.
 */
public class StringUnaryUDF extends VectorExpression {

  public interface IUDFUnaryString {
    Text evaluate(Text s);
  }

  private static final long serialVersionUID = 1L;
  private int colNum;
  private int outputColumn;
  private IUDFUnaryString func;
  private transient final Text s;

  StringUnaryUDF(int colNum, int outputColumn, IUDFUnaryString func) {
    this();
    this.colNum = colNum;
    this.outputColumn = outputColumn;
    this.func = func;
  }

  public StringUnaryUDF() {
    super();
    s = new Text();
  }

  @Override
  public void evaluate(VectorizedRowBatch batch) {

    if (childExpressions != null) {
      super.evaluateChildren(batch);
    }

    BytesColumnVector inputColVector = (BytesColumnVector) batch.cols[colNum];
    int[] sel = batch.selected;
    int n = batch.size;
    byte[][] vector = inputColVector.vector;
    int [] start = inputColVector.start;
    int [] length = inputColVector.length;
    BytesColumnVector outputColVector = (BytesColumnVector) batch.cols[outputColumn];
    boolean[] inputIsNull = inputColVector.isNull;
    boolean[] outputIsNull = outputColVector.isNull;
    outputColVector.initBuffer();
    Text t;

    if (n == 0) {
      //Nothing to do
      return;
    }

    // Design Note: In the future, if this function can be implemented
    // directly to translate input to output without creating new
    // objects, performance can probably be improved significantly.
    // It's implemented in the simplest way now, just calling the
    // existing built-in function.

    // We do not need to do a column reset since we are carefully changing the output.
    outputColVector.isRepeating = false;

    if (inputColVector.isRepeating) {
      if (inputColVector.noNulls || !inputIsNull[0]) {
        // Set isNull before call in case it changes it mind.
        outputIsNull[0] = false;
        s.set(vector[0], start[0], length[0]);
        t = func.evaluate(s);
        setString(outputColVector, 0, t);
      } else {
        outputIsNull[0] = true;
        outputColVector.noNulls = false;
      }
      outputColVector.isRepeating = true;
      return;
    }

    if (inputColVector.noNulls) {
      if (batch.selectedInUse) {

        for(int j = 0; j != n; j++) {
          final int i = sel[j];
          // Set isNull before call in case it changes it mind.
          outputIsNull[i] = false;
          s.set(vector[i], start[i], length[i]);
          t = func.evaluate(s);
          setString(outputColVector, i, t);
        }
      } else {
        // Assume it is almost always a performance win to fill all of isNull so we can
        // safely reset noNulls.
        Arrays.fill(outputIsNull, false);
        outputColVector.noNulls = true;
        for(int i = 0; i != n; i++) {
          s.set(vector[i], start[i], length[i]);
          t = func.evaluate(s);
          setString(outputColVector, i, t);
        }
      }
    } else /* there are nulls in the inputColVector */ {

      // Carefully handle NULLs...
      outputColVector.noNulls = false;

      if (batch.selectedInUse) {
        for(int j = 0; j != n; j++) {
          int i = sel[j];
          outputColVector.isNull[i] = inputColVector.isNull[i]; // setString can override this
          if (!inputColVector.isNull[i]) {
            s.set(vector[i], start[i], length[i]);
            t = func.evaluate(s);
            setString(outputColVector, i, t);
          }
        }
      } else {

        // setString can override this null propagation
        System.arraycopy(inputColVector.isNull, 0, outputColVector.isNull, 0, n);
        for(int i = 0; i != n; i++) {
          if (!inputColVector.isNull[i]) {
            s.set(vector[i], start[i], length[i]);
            t = func.evaluate(s);
            setString(outputColVector, i, t);
          }
        }
      }
    }
  }

  /* Set the output string entry i to the contents of Text object t.
   * If t is a null object reference, record that the value is a SQL NULL.
   */
  private static void setString(BytesColumnVector outV, int i, Text t) {
    if (t == null) {
      outV.noNulls = false;
      outV.isNull[i] = true;
      return;
    }
    outV.setVal(i, t.getBytes(), 0, t.getLength());
  }

  @Override
  public int getOutputColumn() {
    return outputColumn;
  }

  @Override
  public String getOutputType() {
    return "String";
  }

  public int getColNum() {
    return colNum;
  }

  public void setColNum(int colNum) {
    this.colNum = colNum;
  }

  public IUDFUnaryString getFunc() {
    return func;
  }

  public void setFunc(IUDFUnaryString func) {
    this.func = func;
  }

  public void setOutputColumn(int outputColumn) {
    this.outputColumn = outputColumn;
  }

  @Override
  public VectorExpressionDescriptor.Descriptor getDescriptor() {
    VectorExpressionDescriptor.Builder b = new VectorExpressionDescriptor.Builder();
    b.setMode(VectorExpressionDescriptor.Mode.PROJECTION)
        .setNumArguments(1)
        .setArgumentTypes(
            VectorExpressionDescriptor.ArgumentType.STRING_FAMILY)
        .setInputExpressionTypes(
            VectorExpressionDescriptor.InputExpressionType.COLUMN);
    return b.build();
  }
}
