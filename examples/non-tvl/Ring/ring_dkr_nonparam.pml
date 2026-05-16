#define L 8 // channels size

mtype = { one, two, winner };

chan q1 = [L] of {mtype, byte};
chan q2 = [L] of {mtype, byte};
chan q3 = [L] of {mtype, byte};
chan q4 = [L] of {mtype, byte};

byte nr_leaders = 0;

proctype R1() {
    bit Active = 1, know_winner = 0;
    byte mynumber = 2;
    byte nr, maximum = 2, neighbourR;

    printf("MSC: %d\n", mynumber);
    q1!one(mynumber);

end: do
    :: q4?one(nr) ->
        if
        :: Active -> 
            if
            :: nr != maximum ->
                q1!two(nr);
                neighbourR = nr
            :: else ->
                know_winner = 1;
                q1!winner,nr;
            fi
        :: else -> q1!one(nr)
        fi

    :: q4?two(nr) ->
        if
        :: Active -> 
            if
            :: neighbourR > nr && neighbourR > maximum ->
                maximum = neighbourR;
                q1!one(maximum)
            :: else -> Active = 0
            fi
        :: else -> q1!two(nr)
        fi

    :: q4?winner,nr ->
        if
        :: nr != mynumber -> printf("MSC: R1 LOST\n");
        :: else -> 
            printf("MSC: R1 LEADER\n");
            nr_leaders++;
            assert(nr_leaders == 1)
        fi;
        if
        :: know_winner -> skip
        :: else -> q1!winner,nr
        fi;
        break
    od
}

proctype R2() {
    bit Active = 1, know_winner = 0;
    byte mynumber = 4;
    byte nr, maximum = 4, neighbourR;

    printf("MSC: %d\n", mynumber);
    q2!one(mynumber);

end: do
    :: q1?one(nr) ->
        if
        :: Active -> 
            if
            :: nr != maximum ->
                q2!two(nr);
                neighbourR = nr
            :: else ->
                know_winner = 1;
                q2!winner,nr;
            fi
        :: else -> q2!one(nr)
        fi

    :: q1?two(nr) ->
        if
        :: Active -> 
            if
            :: neighbourR > nr && neighbourR > maximum ->
                maximum = neighbourR;
                q2!one(maximum)
            :: else -> Active = 0
            fi
        :: else -> q2!two(nr)
        fi

    :: q1?winner,nr ->
        if
        :: nr != mynumber -> printf("MSC: R2 LOST\n");
        :: else -> 
            printf("MSC: R2 LEADER\n");
            nr_leaders++;
            assert(nr_leaders == 1)
        fi;
        if
        :: know_winner -> skip
        :: else -> q2!winner,nr
        fi;
        break
    od
}

proctype R3() {
    bit Active = 1, know_winner = 0;
    byte mynumber = 1;
    byte nr, maximum = 1, neighbourR;

    printf("MSC: %d\n", mynumber);
    q3!one(mynumber);

end: do
    :: q2?one(nr) ->
        if
        :: Active -> 
            if
            :: nr != maximum ->
                q3!two(nr);
                neighbourR = nr
            :: else ->
                know_winner = 1;
                q3!winner,nr;
            fi
        :: else -> q3!one(nr)
        fi

    :: q2?two(nr) ->
        if
        :: Active -> 
            if
            :: neighbourR > nr && neighbourR > maximum ->
                maximum = neighbourR;
                q3!one(maximum)
            :: else -> Active = 0
            fi
        :: else -> q3!two(nr)
        fi

    :: q2?winner,nr ->
        if
        :: nr != mynumber -> printf("MSC: R3 LOST\n");
        :: else -> 
            printf("MSC: R3 LEADER\n");
            nr_leaders++;
            assert(nr_leaders == 1)
        fi;
        if
        :: know_winner -> skip
        :: else -> q3!winner,nr
        fi;
        break
    od
}

proctype R4() {
    bit Active = 1, know_winner = 0;
    byte mynumber = 3;
    byte nr, maximum = 3, neighbourR;

    printf("MSC: %d\n", mynumber);
    q4!one(mynumber);

end: do
    :: q3?one(nr) ->
        if
        :: Active -> 
            if
            :: nr != maximum ->
                q4!two(nr);
                neighbourR = nr
            :: else ->
                know_winner = 1;
                q4!winner,nr;
            fi
        :: else -> q4!one(nr)
        fi

    :: q3?two(nr) ->
        if
        :: Active -> 
            if
            :: neighbourR > nr && neighbourR > maximum ->
                maximum = neighbourR;
                q4!one(maximum)
            :: else -> Active = 0
            fi
        :: else -> q4!two(nr)
        fi

    :: q3?winner,nr ->
        if
        :: nr != mynumber -> printf("MSC: R4 LOST\n");
        :: else -> 
            printf("MSC: R4 LEADER\n");
            nr_leaders++;
            assert(nr_leaders == 1)
        fi;
        if
        :: know_winner -> skip
        :: else -> q4!winner,nr
        fi;
        break
    od
}

init {
    atomic {
        run R1();
        run R2();
        run R3();
        run R4();
    }
}