// StaticTypeCheck.java

import java.util.*;

// Static type checking for Clite is defined by the functions 
// V and the auxiliary functions typing and typeOf.  These
// functions use the classes in the Abstract Syntax of Clite.


public class StaticTypeCheck
{
	// 현재 Validate 중인 함수에서 리턴을 했는가.
	// 모든 실행 경로 판단.
	private static boolean hasReturn;
	// 에러가 한 번이라도 발생했는가.
	private static boolean hasTypeError;
	
	// Declarations 으로부터 맵 생성
	public static TypeMap typing(Declarations d)
	{
		TypeMap map = new TypeMap();
		for (Declaration di : d)
		{
			map.put(di.v, di.t);
		}
		return map;
	}
	
	// test 가 false 이면 에러 메시지 출력 후 종료.
	public static void check(boolean test, String msg)
	{
		if (test)
		{
			return;
		}
		hasTypeError = true;
		System.err.println(msg);
		// 에러 출력 후 바로 종료 안함.
		//System.exit(1);
	}
	
	// Declarations 체크, 중복 선언 있는지 확인
	public static void V(Declarations d)
	{
		for (int i = 0; i < d.size() - 1; i++)
		{
			for (int j = i + 1; j < d.size(); j++)
			{
				Declaration di = d.get(i);
				Declaration dj = d.get(j);
				check(!(di.v.equals(dj.v)),
						"duplicate declaration: " + dj.v);
			}
		}
	}
	
	// 전체 프로그램 체크.
	// Declarations, body 부분 체크.
	public static void V(Program p)
	{
		//V(p.decpart);
		//V(p.body, typing(p.decpart));
		
		// main 함수 있는지 체크
		check(p.functions.getFunction("main") != null, "Error! Main function not found!");
		
		// Type Rule 10.1
		// 전역 변수 및 함수 이름이 unique 인지 체크
		Declarations globalAndFunctions = new Declarations();
		globalAndFunctions.addAll(p.globals);
		globalAndFunctions.addAll(p.functions.getAllFunctionNames());
		V(globalAndFunctions);
		
		// 전체 프로그램 V
		// -> 각 함수별로 체크
		V(p.functions, typing(p.globals));
		
		// 에러 없다면 출력
		if(!hasTypeError)
		{
			System.out.println("\nNo type errors :P\n");
		}
	}
	
	// 각 함수 별로 Validate 체크
	public static void V(Functions functions, TypeMap tm)
	{
		for (Function function : functions)
		{
			// Type Rule 10.3
			// 전역 변수, 파라미터, 로컬 변수의 TypeMap 생성 및 체크
			TypeMap newMap = new TypeMap();
			newMap.putAll(tm);
			newMap.putAll(typing(function.params));
			newMap.putAll(typing(function.locals));
			
			// Type Rule 10.2
			// 중복 체크
			Declarations localsAndParams = new Declarations();
			localsAndParams.addAll(function.locals);
			localsAndParams.addAll(function.params);
			V(localsAndParams);
			
			// 함수 내부 V
			V(function, newMap, functions);
		}
	}
	
	// 함수 하나의 V
	public static void V(Function function, TypeMap tm, Functions functions)
	{
		// TypeMap 출력
		System.out.println("Function " + function.id + " = ");
		tm.display(functions);
		
		// 리턴 했는가
		hasReturn = false;
		
		// 함수 body 내용 iterate
		Iterator<Statement> it = function.body.members.iterator();
		while (it.hasNext())
		{
			Statement s = it.next();
			// Return 문장 나옴.
			if (s instanceof Return)
			{
				// 리턴은 한 번만 가능.
				check(!hasReturn, "Function " + function.id + " has multiple return statements!");
				hasReturn = true;
				
				V(s, tm, functions);
			}
			else
			{
				// 리턴 뒤에 다른 Statement 가 있으면 안됨.
				check(!hasReturn, "Return must be last statement in function block (in function " + function.id + ")");
				V(s, tm, functions);
			}
		}
		
		// Type Rule 10.4
		// void 아니면 리턴이 필요하다.
		if (!function.t.equals(Type.VOID) && !function.id.equals("main")) // ignore main (we cool like dat)
		{
			check(hasReturn, "Non-void function " + function.id + " missing return statement!");
		}
		
		// Type Rule 10.5
		// void 함수는 리턴이 있으면 안됨
		else if (function.t.equals(Type.VOID))
		{
			check(!hasReturn, "Void function " + function.id + " has return statement when it shouldn't!");
		}
	}
	
	// Call
	public static void V(Call call, TypeMap tm, Functions functions)
	{
		// 호출된 함수 가져옴.
		Function function = functions.getFunction(call.name);
		
		// Type Rule 10.7
		// 호출된 함수의 파라미터와 호출 시의 매개변수 가져옴.
		Iterator<Declaration> funcIt = function.params.iterator();
		Iterator<Expression> callIt = call.args.iterator();
		// 함수의 파라미터 돌면서 체크.
		while (funcIt.hasNext())
		{
			Declaration dec = funcIt.next();
			
			// 파라미터 개수와 매개변수 개수 체크
			check(callIt.hasNext(), "Incorrect number of arguments for function call " + call.name);
			// 없으면 종료
			if (!callIt.hasNext())
			{
				break;
			}
			
			Expression exp = callIt.next();
			
			// 파라미터 타입과 비교
			Type expType = typeOf(exp, tm, functions);
			check(dec.t == expType, "Wrong type in parameter for " + dec.v + " of function " + call.name + " (got a " + expType + ", expected a " + dec.t + ")");
		}
		
		// 파라미터 개수와 매개변수 개수 체크
		check(!callIt.hasNext(), "Incorrect number of arguments for function call " + call.name);
	}
	
	// Expression 의 타입을 tm(TypeMap)에서 가져온다.
	public static Type typeOf(Expression e, TypeMap tm, Functions functions)
	{
		// 값이면 그 값의 타입을 반환
		if (e instanceof Value)
		{
			return ((Value) e).type;
		}
		// 변수이면 변수의 타입을 반환.
		if (e instanceof Variable)
		{
			Variable v = (Variable) e;
			// 선언되지 않은 변수는 에러.
			check(tm.containsKey(v), "undefined variable: " + v);
			return (Type) tm.get(v);
		}
		// Call
		if (e instanceof Call)
		{
			Call c = (Call) e;
			Function f = functions.getFunction(c.name);
			// 선언되지 않은 함수는 에러.
			if(f == null)
			{
				check(f != null, "undefined function: " + c.name);
				return Type.VOID;
			}
			return f.t;
		}
		// Binary 연산자
		if (e instanceof Binary)
		{
			Binary b = (Binary) e;
			// +, -, *, /
			if (b.op.ArithmeticOp())
			{
				if (typeOf(b.term1, tm, functions) == Type.FLOAT)
				{
					return (Type.FLOAT);
				}
				else
				{
					return (Type.INT);
				}
			}
			// &&, ||, <, >, <=, >=, ==, != 등의 연산자는 불 타입.
			if (b.op.RelationalOp() || b.op.BooleanOp())
			{
				return (Type.BOOL);
			}
		}
		// Unary 연산자.
		if (e instanceof Unary)
		{
			Unary u = (Unary) e;
			// !
			if (u.op.NotOp())
			{
				return (Type.BOOL);
			}
			// -
			else if (u.op.NegateOp())
			{
				return typeOf(u.term, tm, functions);
			}
			// int 캐스팅
			else if (u.op.intOp())
			{
				return (Type.INT);
			}
			// float 캐스팅
			else if (u.op.floatOp())
			{
				return (Type.FLOAT);
			}
			// char 캐스팅
			else if (u.op.charOp())
			{
				return (Type.CHAR);
			}
		}
		throw new IllegalArgumentException("should never reach here");
	}
	
	// Expression 을 체크.
	public static void V(Expression e, TypeMap tm, Functions functions)
	{
		// Value. 체크할 것이 없음.
		if (e instanceof Value)
		{
			return;
		}
		// Expression 형태로 함수 call
		if (e instanceof Call)
		{
			// Type Rule 10.6
			// Expression 형태의 Call 은 non-void 만 가능.
			Call call = (Call) e;
			Function function = functions.getFunction(call.name);
			// 함수가 선언 되었는지 확인
			if(function == null)
			{
				check(function != null, "undeclared function: " + call.name);
			}
			else
			{
				check(!function.t.equals(Type.VOID), "Call Expression must be a non-void type function!");
				V((Call) e, tm, functions);
			}
			return;
		}
		// 변수이면 선언 되었는지 체크
		if (e instanceof Variable)
		{
			Variable v = (Variable) e;
			check(tm.containsKey(v)
					, "undeclared variable: " + v);
			return;
		}
		// Binary 연산자.
		if (e instanceof Binary)
		{
			Binary b = (Binary) e;
			Type typ1 = typeOf(b.term1, tm, functions);
			Type typ2 = typeOf(b.term2, tm, functions);
			
			// 각각을 체크
			V(b.term1, tm, functions);
			V(b.term2, tm, functions);
			
			// +, -, *, /
			if (b.op.ArithmeticOp())
			{
				check(typ1 == typ2 &&
								(typ1 == Type.INT || typ1 == Type.FLOAT)
						, "type error for " + b.op);
			}
			// <, >, <=, >=, ==, !=
			else if (b.op.RelationalOp())
			{
				check(typ1 == typ2, "type error for " + b.op);
			}
			// &&, ||
			else if (b.op.BooleanOp())
			{
				check(typ1 == Type.BOOL && typ2 == Type.BOOL,
						b.op + ": non-bool operand");
			}
			else
			{
				throw new IllegalArgumentException("should never reach here");
			}
			return;
		}
		
		// student exercise
		// Unary 연산자.
		else if (e instanceof Unary)
		{
			Unary u = (Unary) e;
			
			Type t = typeOf(u.term, tm, functions);
			
			V(u.term, tm, functions);
			
			// ! 연산자
			// 불 타입이어야 함.
			if (u.op.NotOp())
			{
				check(t == Type.BOOL,
						u.op + ": non-bool operand");
			}
			// - 연산자
			// float, int 에만 사용 가능.
			else if (u.op.NegateOp())
			{
				check(t == Type.INT || t == Type.FLOAT,
						u.op + ": non-int or non-float operand");
			}
			// int 캐스팅 연산자.
			// float, char 에만 사용 가능.
			else if (u.op.intOp())
			{
				check(t == Type.FLOAT || t == Type.CHAR,
						u.op + ": non-float or non-char operand");
			}
			// float 캐스팅 연산자.
			// int 에만 사용 가능.
			else if (u.op.floatOp())
			{
				check(t == Type.INT,
						u.op + ": non-int operand");
			}
			// char 캐스팅 연산자.
			// int 에만 사용 가능.
			else if (u.op.charOp())
			{
				check(t == Type.INT,
						u.op + ": non-int operand");
			}
			else
			{
				throw new IllegalArgumentException("should never reach here");
			}
			return;
		}
		throw new IllegalArgumentException("should never reach here");
	}
	
	// Statement 검증
	public static void V(Statement s, TypeMap tm, Functions functions)
	{
		// null 이면 에러
		if (s == null)
		{
			throw new IllegalArgumentException("AST error: null statement");
		}
		// Skip (;)이면 ㅇㅋ
		if (s instanceof Skip)
		{
			return;
		}
		// Assignment
		if (s instanceof Assignment)
		{
			Assignment a = (Assignment) s;
			// 선언 되어있는지 체크
			check(tm.containsKey(a.target)
					, "undefined target in assignment: " + a.target);
			V(a.source, tm, functions);
			Type ttype = (Type) tm.get(a.target);
			Type srctype = typeOf(a.source, tm, functions);
			
			// Type Rule 10.8
			// 함수의 리턴형과 연산 타입이 같아야 됨.
			// target, source 서로 다를 경우
			if (ttype != srctype)
			{
				// int 를 float 에 대입
				if (ttype == Type.FLOAT)
				{
					check(srctype == Type.INT
							, "mixed mode assignment to " + a.target);
				}
				// char 를 int 에 대입
				else if (ttype == Type.INT)
				{
					check(srctype == Type.CHAR
							, "mixed mode assignment to " + a.target);
				}
				// 그 외는 에러. (float 를 int 에 대입 등)
				else
				{
					check(false
							, "mixed mode assignment to " + a.target);
				}
			}
			return;
		}
		
		// student exercise
		// Block 검증. (Statement 여러개)
		if (s instanceof Block)
		{
			Block b = (Block) s;
			
			// Block 내의 모든 Statement 검증
			for (Statement statement : b.members)
			{
				V(statement, tm, functions);
			}
			
			return;
		}
		// Conditional
		if (s instanceof Conditional)
		{
			Conditional c = (Conditional) s;
			boolean ifReturn = false, elseReturn = false;
			
			// 조건문, then, else 각각을 V
			V(c.test, tm, functions);
			V(c.thenbranch, tm, functions);
			ifReturn = hasReturn;
			hasReturn = false;
			
			V(c.elsebranch, tm, functions);
			elseReturn = hasReturn;
			
			// if와 else 둘 다에서 Return 하면 Return 한 것.
			// 아니면 다음에 Return 이 나와야 함.
			hasReturn = ifReturn && elseReturn;
			
			return;
		}
		// Loop
		if (s instanceof Loop)
		{
			Loop l = (Loop) s;
			
			// 조건문과 body 각각을 V
			V(l.test, tm, functions);
			V(l.body, tm, functions);
			
			// 루프 밖에서도 Return 필요
			hasReturn = false;
			
			return;
		}
		// Statement 형태로 함수 call
		// Call 만 있는 경우
		if (s instanceof Call)
		{
			// Type Rule 10.6
			// Statement 형태의 Call 은 void 만 가능.
			Call call = (Call) s;
			Function function = functions.getFunction(call.name);
			check(function.t.equals(Type.VOID), "Call Statement must be a void type function!");
			V((Call) s, tm, functions);
			return;
		}
		// Return
		if (s instanceof Return)
		{
			// Return
			hasReturn = true;
			
			Return r = (Return) s;
			Function f = functions.getFunction(r.target.toString());
			Type t = typeOf(r.result, tm, functions);
			check(t.equals(f.t), "Return expression doesn't match function's return type! (got a " + t + ", expected a " + f.t + ")");
			
			return;
		}
		throw new IllegalArgumentException("should never reach here");
	}
	
	public static void main(String args[])
	{
		//Parser parser  = new Parser(new Lexer(args[0]));
		// 명령 인자방식이 아닌 직접 입력 방식 사용
		String fileName = "../Test Programs/fib.cpp";
		Parser parser = new Parser(new Lexer(fileName));
		
		// 프로그램 파싱
		Program prog = parser.program();
		prog.display();           // student exercise
		
		// Type Map 출력, 타입 체킹.
		System.out.println("\nBegin type checking...");
		System.out.println("Globals = ");
		TypeMap map = typing(prog.globals);
		map.display();
		
		V(prog);
	} //main
	
} // class StaticTypeCheck

