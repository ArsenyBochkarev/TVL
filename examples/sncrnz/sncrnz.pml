// An example of a synchronously valid but asynchronously invalid protocol

proctype R1() {
 r13 ! true;
 r12 ! true;

 Fin_1 = true;
}

proctype R2() {
 r12 ? true;
 r23 ! true;

 Fin_2 = true;
}

proctype R3() {
    do
    :: true -> r23 ? true; break;
    :: true -> r13 ? true; mes = true;
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