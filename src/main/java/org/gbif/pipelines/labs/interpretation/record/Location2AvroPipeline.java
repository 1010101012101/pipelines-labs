package org.gbif.pipelines.labs.interpretation.record;

import org.gbif.pipelines.config.DataPipelineOptionsFactory;
import org.gbif.pipelines.config.DataProcessingPipelineOptions;
import org.gbif.pipelines.core.ws.config.Config;
import org.gbif.pipelines.core.ws.config.HttpConfigFactory;
import org.gbif.pipelines.io.avro.ExtendedRecord;
import org.gbif.pipelines.io.avro.location.LocationRecord;
import org.gbif.pipelines.transform.record.LocationRecordTransform;
import org.gbif.pipelines.transform.validator.UniqueOccurrenceIdTransform;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.io.AvroIO;
import org.apache.beam.sdk.transforms.Values;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Location2AvroPipeline {

  private static final Logger LOG = LoggerFactory.getLogger(Location2AvroPipeline.class);

  private static final String WS_DEV = "https://api.gbif-dev.org/";

  public static void main(String[] args) {

    // Create a pipeline
    DataProcessingPipelineOptions options = DataPipelineOptionsFactory.create(args);
    String inputFile = options.getInputPath();
    String targetDirectory = options.getTargetPath();

    Pipeline p = Pipeline.create(options);

    // ws config
    Config wsConfig = HttpConfigFactory.createConfigFromUrl(WS_DEV);

    // Transforms to use
    UniqueOccurrenceIdTransform uniquenessTransform =
        UniqueOccurrenceIdTransform.create().withAvroCoders(p);
    LocationRecordTransform locationTransform =
        LocationRecordTransform.create(wsConfig).withAvroCoders(p);

    // STEP 1: Read Avro files
    PCollection<ExtendedRecord> verbatimRecords =
        p.apply("Read Avro files", AvroIO.read(ExtendedRecord.class).from(inputFile));

    // STEP 2: Validate ids uniqueness
    PCollectionTuple uniqueTuple = verbatimRecords.apply(uniquenessTransform);
    PCollection<ExtendedRecord> extendedRecords = uniqueTuple.get(uniquenessTransform.getDataTag());

    // STEP 3: Run the main transform
    PCollectionTuple locationRecordTuple = extendedRecords.apply(locationTransform);
    PCollection<LocationRecord> locationRecords =
        locationRecordTuple.get(locationTransform.getDataTag()).apply(Values.create());

    // STEP 4: Save to an avro file
    locationRecords.apply("Write Avro files", AvroIO.write(LocationRecord.class).to(targetDirectory));

    // Run
    LOG.info("Starting the pipeline");
    PipelineResult result = p.run();
    result.waitUntilFinish();
    LOG.info("Pipeline finished with state: {} ", result.getState());
  }
}
