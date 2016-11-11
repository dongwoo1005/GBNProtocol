# GBN Protocol File Transfer
      
## How to compile and make the programs
- You need minimum of JDK 7
- Available command:
`make all`
`make receiver`
`make sender`

## Receiver program
~~~~
Number of parameters: 4
Parameter:
      $1: <nEmulator hostname>
      $2: <port3 for emulator to receive ACKs from receiver>
      $3: <port4 for receiver to receive data from emulator>
      $5: <output file>
How to run:
      java Receiver $1 $2 $3 $4
~~~~

## Sender program
~~~~
Number of parameters: 4
Parameter:
      $1: <nEmulator hostname>
      $2: <port1 for emulator to receive data from sender>
      $3: <port2 for sender to receive ACKs from emulator>
      $4: <input file>
How to run:
      java Sender $1 $2 $3 $4
~~~~

## Built and Tested Machines
1. Built and tested on three different student.cs.machines
      - ubuntu1404-002.student.uwaterloo.ca
      - ubuntu1404-004.student.uwaterloo.ca
      - ubuntu1404-010.student.uwaterloo.ca
