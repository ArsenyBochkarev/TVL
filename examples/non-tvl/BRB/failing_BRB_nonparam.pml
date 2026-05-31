mtype = { V0, V1, SE, AC };

byte node_sv[3];

bool echo_to_0[4];
bool echo_to_1[4];
bool echo_to_2[4];

bool initialized = false;

proctype Node0() {
    byte next_sv;

    do
    :: atomic {
        next_sv = node_sv[0];

        if
            // Wait for 5 msgs
            :: (echo_to_0[0] + echo_to_0[1] + echo_to_0[2] + echo_to_0[3] >= 5) && node_sv[0] != AC -> 
                next_sv = AC;

            // Wait for 4 msgs
            :: (echo_to_0[0] + echo_to_0[1] + echo_to_0[2] + echo_to_0[3] < 5) && 
               (echo_to_0[0] + echo_to_0[1] + echo_to_0[2] + echo_to_0[3] >= 4 || node_sv[0] == V1) && node_sv[0] != SE -> 
                next_sv = SE;

            // No else branch
        fi;

        if
            :: (node_sv[0] == V0 || node_sv[0] == V1) && (next_sv == SE || next_sv == AC) -> 
                echo_to_0[0] = true; 
                echo_to_1[0] = true; 
                echo_to_2[0] = true;
            :: else -> skip;
        fi;

        node_sv[0] = next_sv;

        if
        :: node_sv[0] == AC -> break;
        :: else -> skip;
        fi;
    }
    od
}

proctype Node1() {
    byte nrcvd;
    byte next_sv;

    do
    :: atomic {
        nrcvd = echo_to_1[0] + echo_to_1[1] + echo_to_1[2] + echo_to_1[3];
        next_sv = node_sv[1];

        if
            :: (nrcvd >= 3) -> next_sv = AC;
            :: (nrcvd < 3) && (nrcvd >= 2 || node_sv[1] == V1) -> next_sv = SE;
            :: else -> skip;
        fi;

        if
            :: (node_sv[1] == V0 || node_sv[1] == V1) && (next_sv == SE || next_sv == AC) -> 
                echo_to_0[1] = true; 
                echo_to_1[1] = true; 
                echo_to_2[1] = true;
            :: else -> skip;
        fi;

        node_sv[1] = next_sv;
    }
    od
}

proctype Node2() {
    byte nrcvd;
    byte next_sv;

    do
    :: atomic {
        nrcvd = echo_to_2[0] + echo_to_2[1] + echo_to_2[2] + echo_to_2[3];
        next_sv = node_sv[2];

        if
            :: (nrcvd >= 3) -> next_sv = AC;
            :: (nrcvd < 3) && (nrcvd >= 2 || node_sv[2] == V1) -> next_sv = SE;
            :: else -> skip;
        fi;

        if
            :: (node_sv[2] == V0 || node_sv[2] == V1) && (next_sv == SE || next_sv == AC) -> 
                echo_to_0[2] = true; 
                echo_to_1[2] = true; 
                echo_to_2[2] = true;
            :: else -> skip;
        fi;

        node_sv[2] = next_sv;
    }
    od
}

proctype Node3() {
    do
    :: atomic { echo_to_0[3] = true; }
    :: atomic { echo_to_1[3] = true; }
    :: atomic { echo_to_2[3] = true; }
    :: break;
    od
}

init {
    atomic {
        if :: node_sv[0] = V0; :: node_sv[0] = V1; fi;
        if :: node_sv[1] = V0; :: node_sv[1] = V1; fi;
        if :: node_sv[2] = V0; :: node_sv[2] = V1; fi;
        
        initialized = true;

        run Node0();
        run Node1();
        run Node2();
        run Node3();
    }
}

#define ex_acc      (node_sv[0] == AC || node_sv[1] == AC || node_sv[2] == AC)
#define all_acc     (node_sv[0] == AC && node_sv[1] == AC && node_sv[2] == AC)
#define prec_unforg (node_sv[0] == V0 && node_sv[1] == V0 && node_sv[2] == V0)
#define prec_corr   (node_sv[0] == V1 && node_sv[1] == V1 && node_sv[2] == V1)

ltl relay  { []((initialized && ex_acc) -> <>all_acc) }
ltl unforg { []((initialized && prec_unforg) -> []!ex_acc) }
ltl corr   { []((initialized && prec_corr) -> <>all_acc) }