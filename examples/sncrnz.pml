// An example of a synchronously valid but asynchronously invalid protocol

proctype R1() {

 r13 ! true; // send Y>>R3;
 r12 ! true; // send X>>R2;

 Fin_1 = true;
}

proctype R2() {

 r12 ? true; 	 // wait X<<R1;
 r23 ! true; // send X>>R3;

 Fin_2 = true;
}

proctype R3() {

    do
    :: true -> r23 ? true; break; 		// case X<<R2: break
    :: true -> r13 ? true; mes = true; // case Y<<R1
    od;

	Fin_3 = true;
}

init {
	atomic{
	run R1();
	run R2();
	run R3();
	}
}