package io.opentelemetry.example;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.exporters.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.TracerSdkFactory;
import io.opentelemetry.sdk.trace.export.SimpleSpansProcessor;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.TracerFactory;

public class JaegerExample {
  // Jaeger Endpoint URL and PORT
  private String ip; // = "jaeger";
  private int port; // = 14250;
  // OTel API
  private Tracer tracer;
  private TracerSdkFactory tracerFactory;
  // Create a channel towards Jaeger end point
  private ManagedChannel jaegerChannel;
  // Export traces to Jaeger
  private JaegerGrpcSpanExporter jaegerExporter;

  public JaegerExample(String ip, int port) {
    this.ip = ip;
    this.port = port;
    // Instantiate the OTel factory
    this.tracerFactory = OpenTelemetrySdk.getTracerFactory();
    // Give a name to the tracer
    this.tracer = tracerFactory.get("io.opentelemetry.example.JaegerExample");
  }

  private void setupJaegerExporter() {
    // Create a channel towards Jaeger end point
    this.jaegerChannel = ManagedChannelBuilder.forAddress(ip, port).build();
    // Export traces to Jaeger
    this.jaegerExporter =
        JaegerGrpcSpanExporter.newBuilder()
            .setServiceName("example")
            .setChannel(this.jaegerChannel)
            .setDeadline(10)
            .build();
    
    // Set to process the spans by the Jaeger Exporter
    this.tracerFactory.addSpanProcessor(SimpleSpansProcessor.newBuilder(this.jaegerExporter).build());
  }

  private void myWonderfulUseCase() {
    // Generate a span
    Span span = this.tracer.spanBuilder("Start my wonderful use case").startSpan();
    span.addEvent("Event 0");
    // execute my use case - here we simulate a wait
    doWork();
    span.addEvent("Event 1");
    span.end();
    this.jaegerExporter.shutdown();
  }

  private void doWork() {
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
    }
  }

  public static void main(String[] args) {
    // Parsing the input
    if (args.length < 2) {
      System.out.println("Missing [hostname] [port]");
      System.exit(1);
    }
    String ip = args[0];
    int port = Integer.parseInt(args[1]);

    // Start the example
    JaegerExample example = new JaegerExample(ip, port);
    example.setupJaegerExporter();
    example.myWonderfulUseCase();
    System.out.println("Bye");
  }
}
