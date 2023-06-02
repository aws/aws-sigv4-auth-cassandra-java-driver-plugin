CREATE KEYSPACE "testkeyspace" WITH REPLICATION = {'class': 'SingleRegionStrategy'};

CREATE TABLE "testkeyspace"."testconf"(
  "id" ascii,
  "category" ascii,
  PRIMARY KEY("id")
)
WITH CUSTOM_PROPERTIES = {
  'capacity_mode':{
  'throughput_mode':'PAY_PER_REQUEST'
  },
  'point_in_time_recovery':{
  'status':'enabled'
  },
  'encryption_specification':{
  'encryption_type':'AWS_OWNED_KMS_KEY'
  }
} ;
