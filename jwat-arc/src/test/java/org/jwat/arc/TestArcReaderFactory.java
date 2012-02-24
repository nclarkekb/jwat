/**
 * Java Web Archive Toolkit - Software to read and validate ARC, WARC
 * and GZip files. (http://jwat.org/)
 * Copyright 2011-2012 Netarkivet.dk (http://netarkivet.dk/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jwat.arc;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.jwat.arc.ArcReader;
import org.jwat.arc.ArcReaderFactory;
import org.jwat.arc.ArcRecord;
import org.jwat.arc.ArcVersionBlock;

@RunWith(Parameterized.class)
public class TestArcReaderFactory {

    private int expected_records;
    private String arcFile;

    @Parameters
    public static Collection<Object[]> configs() {
        return Arrays.asList(new Object[][] {
                {299, "IAH-20080430204825-00000-blackbook.arc"},
                {299, "IAH-20080430204825-00000-blackbook.arc.gz"},
        });
    }

    public TestArcReaderFactory(int records, String arcFile) {
        this.expected_records = records;
        this.arcFile = arcFile;
    }

    @Test
    public void test_arcreaderfactory_autodetect() {
        boolean bDebugOutput = System.getProperty("jwat.debug.output") != null;

        InputStream in;

        ArcReader reader;
        ArcVersionBlock version;
        ArcRecord arcRecord;

        int records = 0;
        int errors = 0;
        int warnings = 0;

        try {
            /*
             * Auto detect unbuffered.
             */

            records = 0;
            errors = 0;
            warnings = 0;

            in = this.getClass().getClassLoader().getResourceAsStream(arcFile);

            reader = ArcReaderFactory.getReader(in);

            version = reader.getVersionBlock();

            if (version != null) {
                if (bDebugOutput) {
                    RecordDebugBase.printVersionBlock(version);
                }

                boolean b = true;
                while ( b ) {
                    arcRecord = reader.getNextRecord();
                    if (arcRecord != null) {
                        if (bDebugOutput) {
                            RecordDebugBase.printRecord(arcRecord);
                        }

                        ++records;

                        if (arcRecord.diagnostics.hasErrors()) {
                            errors += arcRecord.diagnostics.getErrors().size();
                        }
                        if (arcRecord.diagnostics.hasWarnings()) {
                            warnings += arcRecord.diagnostics.getWarnings().size();
                        }
                    }
                    else {
                        b = false;
                    }
                }

                if (bDebugOutput) {
                    RecordDebugBase.printStatus(records, errors, warnings);
                }
            }

            reader.close();
            in.close();

            Assert.assertEquals(expected_records, records);
            Assert.assertEquals(0, errors);
            Assert.assertEquals(0, warnings);

            /*
             * Auto detect buffered.
             */

            records = 0;
            errors = 0;
            warnings = 0;

            in = this.getClass().getClassLoader().getResourceAsStream(arcFile);

            reader = ArcReaderFactory.getReader(in, 8192);

            version = reader.getVersionBlock();

            if (version != null) {
                if (bDebugOutput) {
                    RecordDebugBase.printVersionBlock(version);
                }

                boolean b = true;
                while ( b ) {
                    arcRecord = reader.getNextRecord();
                    if (arcRecord != null) {
                        if (bDebugOutput) {
                            RecordDebugBase.printRecord(arcRecord);
                        }

                        ++records;

                        if (arcRecord.diagnostics.hasErrors()) {
                            errors += arcRecord.diagnostics.getErrors().size();
                        }
                        if (arcRecord.diagnostics.hasWarnings()) {
                            warnings += arcRecord.diagnostics.getWarnings().size();
                        }
                    }
                    else {
                        b = false;
                    }
                }

                if (bDebugOutput) {
                    RecordDebugBase.printStatus(records, errors, warnings);
                }
            }

            reader.close();
            in.close();

            Assert.assertEquals(expected_records, records);
            Assert.assertEquals(0, errors);
            Assert.assertEquals(0, warnings);
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

}
