package server

import annotation.GRPCServer
import io.grpc.stub.StreamObserver
import top.youngxhui.grpc.gencode.HelloRequest
import top.youngxhui.grpc.gencode.HelloResponse
import top.youngxhui.grpc.gencode.HelloServiceGrpc.HelloServiceImplBase

@GRPCServer
class HelloServer : HelloServiceImplBase() {
    override fun hello(request: HelloRequest, responseObserver: StreamObserver<HelloResponse>) {
        val name = request.name
        val response = HelloResponse.newBuilder().setGreeting("Hello $name").build()
        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }
}