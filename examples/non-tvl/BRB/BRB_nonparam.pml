mtype = { V0, V1, SE, AC };

byte sv[3];

bool echo_to_0[4];
bool echo_to_1[4];
bool echo_to_2[4];

bool initialized = false;

proctype Node0() {
    byte nrcvd;
    byte next_sv;

    do
    :: atomic {
        nrcvd = echo_to_0[0] + echo_to_0[1] + echo_to_0[2] + echo_to_0[3];
        next_sv = sv[0];

        if
            :: (nrcvd >= 3) -> next_sv = AC;
            :: (nrcvd < 3) && (nrcvd >= 2 || sv[0] == V1) -> next_sv = SE;
            :: else -> skip;
        fi;

        if
            :: (sv[0] == V0 || sv[0] == V1) && (next_sv == SE || next_sv == AC) -> 
                echo_to_0[0] = true; 
                echo_to_1[0] = true; 
                echo_to_2[0] = true;
            :: else -> skip;
        fi;

        sv[0] = next_sv;
    }
    od
}

proctype Node1() {
    byte nrcvd;
    byte next_sv;

    do
    :: atomic {
        nrcvd = echo_to_1[0] + echo_to_1[1] + echo_to_1[2] + echo_to_1[3];
        next_sv = sv[1];

        if
            :: (nrcvd >= 3) -> next_sv = AC;
            :: (nrcvd < 3) && (nrcvd >= 2 || sv[1] == V1) -> next_sv = SE;
            :: else -> skip;
        fi;

        if
            :: (sv[1] == V0 || sv[1] == V1) && (next_sv == SE || next_sv == AC) -> 
                echo_to_0[1] = true; 
                echo_to_1[1] = true; 
                echo_to_2[1] = true;
            :: else -> skip;
        fi;

        sv[1] = next_sv;
    }
    od
}

proctype Node2() {
    byte nrcvd;
    byte next_sv;

    do
    :: atomic {
        nrcvd = echo_to_2[0] + echo_to_2[1] + echo_to_2[2] + echo_to_2[3];
        next_sv = sv[2];

        if
            :: (nrcvd >= 3) -> next_sv = AC;
            :: (nrcvd < 3) && (nrcvd >= 2 || sv[2] == V1) -> next_sv = SE;
            :: else -> skip;
        fi;

        if
            :: (sv[2] == V0 || sv[2] == V1) && (next_sv == SE || next_sv == AC) -> 
                echo_to_0[2] = true; 
                echo_to_1[2] = true; 
                echo_to_2[2] = true;
            :: else -> skip;
        fi;

        sv[2] = next_sv;
    }
    od
}

proctype Node3() {
    do
    :: atomic { echo_to_0[3] = true; }
    :: atomic { echo_to_1[3] = true; }
    :: atomic { echo_to_2[3] = true; }
    :: skip;
    od
}

init {
    atomic {
        if :: sv[0] = V0; :: sv[0] = V1; fi;
        if :: sv[1] = V0; :: sv[1] = V1; fi;
        if :: sv[2] = V0; :: sv[2] = V1; fi;
        
        initialized = true;

        run Node0();
        run Node1();
        run Node2();
        run Node3();
    }
}

#define ex_acc      (sv[0] == AC || sv[1] == AC || sv[2] == AC)
#define all_acc     (sv[0] == AC && sv[1] == AC && sv[2] == AC)
#define prec_unforg (sv[0] == V0 && sv[1] == V0 && sv[2] == V0)
#define prec_corr   (sv[0] == V1 && sv[1] == V1 && sv[2] == V1)

ltl relay  { []((initialized && ex_acc) -> <>all_acc) }
ltl unforg { []((initialized && prec_unforg) -> []!ex_acc) }
ltl corr   { []((initialized && prec_corr) -> <>all_acc) }