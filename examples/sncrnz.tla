---------------------------- MODULE sncrnz ----------------------------
EXTENDS Integers, Sequences, TLC

(* --algorithm Protocol

variables
    r13 = <<>>,
    r12 = <<>>,
    r23 = <<>>,

    Fin_1 = FALSE,
    Fin_2 = FALSE,
    Fin_3 = FALSE,
    mes = FALSE;

fair process R1 = "R1"
begin
Send_Y:
    r13 := Append(r13, TRUE); \* r13 ! true
Send_X:
    r12 := Append(r12, TRUE); \* r12 ! true
Set_Fin1:
    Fin_1 := TRUE;
end process;

fair process R2 = "R2"
begin
Receive_X:
    await r12 /= <<>>;
    r12 := Tail(r12); \* r12 ? true
Send_X_to_R3:
    r23 := Append(r23, TRUE); \* r23 ! true
Set_Fin2:
    Fin_2 := TRUE;
end process;

fair process R3 = "R3"
begin
Loop:
    while TRUE do
        either
            await r23 /= <<>>;
            r23 := Tail(r23);
            goto End_R3;
        or
            await r13 /= <<>>;
            r13 := Tail(r13);
            mes := TRUE;
        end either;
    end while;

End_R3:
    Fin_3 := TRUE;
end process;

end algorithm; *)