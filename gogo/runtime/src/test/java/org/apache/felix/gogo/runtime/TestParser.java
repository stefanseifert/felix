/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.gogo.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.felix.service.command.CommandSession;
import org.apache.felix.service.command.Function;

public class TestParser extends BaseTestCase
{
    int beentheredonethat = 0;

    public void testEvaluatation() throws Exception
    {
        m_ctx.addCommand("echo", this);
        m_ctx.addCommand("capture", this);

        assertEquals("a", m_ctx.execute("echo a | capture"));
        assertEquals("a", m_ctx.execute("(echo a) | capture"));
        assertEquals("a", m_ctx.execute("((echo a)) | capture"));
    }

    public void testUnknownCommand() throws Exception
    {
        try
        {
            m_ctx.execute("echo");
            fail("Execution should have failed due to missing command");
        }
        catch (IllegalArgumentException e)
        {
            // expected
        }
    }

    public void testSpecialValues() throws Exception
    {
        assertEquals(false, m_ctx.execute("false"));
        assertEquals(true, m_ctx.execute("true"));
        assertEquals(null, m_ctx.execute("null"));
    }

    public void testQuotes() throws Exception
    {
        m_ctx.addCommand("echo", this);
        m_ctx.set("c", "a");

        assertEquals("a b", m_ctx.execute("echo a b"));
        assertEquals("a b", m_ctx.execute("echo 'a b'"));
        assertEquals("a b", m_ctx.execute("echo \"a b\""));
        assertEquals("a b", m_ctx.execute("echo a  b"));
        assertEquals("a  b", m_ctx.execute("echo 'a  b'"));
        assertEquals("a  b", m_ctx.execute("echo \"a  b\""));
        assertEquals("a b", m_ctx.execute("echo $c  b"));
        assertEquals("$c  b", m_ctx.execute("echo '$c  b'"));
        assertEquals("a  b", m_ctx.execute("echo \"$c  b\""));
        assertEquals("a b", m_ctx.execute("echo ${c}  b"));
        assertEquals("${c}  b", m_ctx.execute("echo '${c}  b'"));
        assertEquals("a  b", m_ctx.execute("echo \"${c}  b\""));
        assertEquals("aa", m_ctx.execute("echo $c$c"));
        assertEquals("a ;a", m_ctx.execute("echo a\\ \\;a"));
        assertEquals("baabab", m_ctx.execute("echo b${c}${c}b${c}b"));

        m_ctx.set("d", "a  b ");
        assertEquals("a  b ", m_ctx.execute("echo \"$d\""));
    }

    public void testScope() throws Exception
    {
        m_ctx.addCommand("echo", this);
        assertEquals("$a", m_ctx.execute("test:echo \\$a"));
        assertEquals("file://poo", m_ctx.execute("test:echo file://poo"));
    }

    public void testPipe() throws Exception
    {
        m_ctx.addCommand("echo", this);
        m_ctx.addCommand("capture", this);
        m_ctx.addCommand("grep", this);
        m_ctx.addCommand("echoout", this);
        m_ctx.execute("myecho = { echoout $args }");
        assertEquals("def", m_ctx.execute("echo def|grep d.*|capture"));
        assertEquals("def", m_ctx.execute("echoout def|grep d.*|capture"));
        assertEquals("def", m_ctx.execute("myecho def|grep d.*|capture"));
        assertEquals("def",
            m_ctx.execute("(echoout abc; echoout def; echoout ghi)|grep d.*|capture"));
        assertEquals("", m_ctx.execute("echoout def; echoout ghi | grep d.* | capture"));
        assertEquals("hello world", m_ctx.execute("echo hello world|capture"));
        assertEquals("defghi",
            m_ctx.execute("(echoout abc; echoout def; echoout ghi)|grep 'def|ghi'|capture"));
    }

    public void testAssignment() throws Exception
    {
        m_ctx.addCommand("echo", this);
        m_ctx.addCommand("grep", this);
        assertEquals("a", m_ctx.execute("a = a; echo ${$a}"));

        assertEquals("hello", m_ctx.execute("echo hello"));
        assertEquals("hello", m_ctx.execute("a = (echo hello)"));
        //assertEquals("a", m_ctx.execute("a = a; echo $(echo a)")); // #p2 - no eval in var expansion
        assertEquals("3", m_ctx.execute("a=3; echo $a"));
        assertEquals("3", m_ctx.execute("a = 3; echo $a"));
        assertEquals("a", m_ctx.execute("a = a; echo ${$a}"));
    }

    public void testComment() throws Exception
    {
        m_ctx.addCommand("echo", this);
        assertEquals("1", m_ctx.execute("echo 1 // hello"));
    }

    public void testClosure() throws Exception
    {
        m_ctx.addCommand("echo", this);
        m_ctx.addCommand("capture", this);

        assertEquals("a", m_ctx.execute("e = { echo $1 } ; e a   b"));
        assertEquals("b", m_ctx.execute("e = { echo $2 } ; e a   b"));
        assertEquals("b", m_ctx.execute("e = { eval $args } ; e echo  b"));
        assertEquals("ca b", m_ctx.execute("e = { echo c$args } ; e a  b"));
        assertEquals("c a b", m_ctx.execute("e = { echo c $args } ; e a  b"));
        assertEquals("ca  b", m_ctx.execute("e = { echo c$args } ; e 'a  b'"));
    }

    public void testArray() throws Exception
    {
        m_ctx.set("echo", this);
        assertEquals("http://www.aqute.biz?com=2&biz=1",
            m_ctx.execute("['http://www.aqute.biz?com=2&biz=1'] get 0"));
        assertEquals("{a=2, b=3}", m_ctx.execute("[a=2 b=3]").toString());
        assertEquals(3L, m_ctx.execute("[a=2 b=3] get b"));
        assertEquals("[3, 4]", m_ctx.execute("[1 2 [3 4] 5 6] get 2").toString());
        assertEquals(5, m_ctx.execute("[1 2 [3 4] 5 6] size"));
    }

    public void testParentheses()
    {
        Parser parser = new Parser("(a|b)|(d|f)");
        List<List<List<Token>>> p = parser.program();
        assertEquals("a|b", p.get(0).get(0).get(0).toString());

        parser = new Parser("grep (d.*)|grep (d|f)");
        p = parser.program();
        assertEquals("d.*", p.get(0).get(0).get(1).toString());
    }

    public void testEcho() throws Exception
    {
        m_ctx.addCommand("echo", this);
        m_ctx.execute("echo peter");
    }

    public void grep(String match) throws IOException
    {
        Pattern p = Pattern.compile(match);
        BufferedReader rdr = new BufferedReader(new InputStreamReader(System.in));
        String s = rdr.readLine();
        while (s != null)
        {
            if (p.matcher(s).find())
            {
                System.out.println(s);
            }
            s = rdr.readLine();
        }
    }

    public String capture() throws IOException
    {
        StringWriter sw = new StringWriter();
        BufferedReader rdr = new BufferedReader(new InputStreamReader(System.in));
        String s = rdr.readLine();
        while (s != null)
        {
            sw.write(s);
            s = rdr.readLine();
        }
        return sw.toString();
    }

    public void testVars() throws Exception
    {
        m_ctx.addCommand("echo", this);

        assertEquals("", m_ctx.execute("echo ${very.likely.that.this.does.not.exist}"));
        assertNotNull(m_ctx.execute("echo ${java.shell.name}"));
        assertEquals("a", m_ctx.execute("a = a; echo ${a}"));
    }

    public void testFunny() throws Exception
    {
        m_ctx.addCommand("echo", this);
        assertEquals("a", m_ctx.execute("echo a") + "");
        assertEquals("a", m_ctx.execute("eval (echo echo) a") + "");
        //assertEquals("a", m_ctx.execute("((echo echo) echo) (echo a)") + "");
        assertEquals("3", m_ctx.execute("[a=2 (echo b)=(echo 3)] get b").toString());
    }

    public CharSequence echo(Object args[])
    {
        if (args == null)
        {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (Object arg : args)
        {
            if (arg != null)
            {
                if (sb.length() > 0)
                    sb.append(' ');
                sb.append(arg);
            }
        }
        return sb.toString();
    }

    public void echoout(Object args[])
    {
        System.out.println(echo(args));
    }

    public void testContext() throws Exception
    {
        m_ctx.addCommand("ls", this);
        beentheredonethat = 0;
        m_ctx.execute("ls");
        assertEquals(1, beentheredonethat);

        beentheredonethat = 0;
        m_ctx.execute("ls 10");
        assertEquals(10, beentheredonethat);

        beentheredonethat = 0;
        m_ctx.execute("ls a b c d e f g h i j");
        assertEquals(10, beentheredonethat);

        beentheredonethat = 0;
        Integer result = (Integer) m_ctx.execute("ls (ls 5)");
        assertEquals(10, beentheredonethat);
        assertEquals((Integer) 5, result);
    }

    public void ls()
    {
        beentheredonethat++;
        System.out.println("ls(): Yes!");
    }

    public int ls(int onoff)
    {
        beentheredonethat += onoff;
        System.out.println("ls(int) " + onoff);
        return onoff;
    }

    public void ls(Object args[])
    {
        beentheredonethat = args.length;
        System.out.print("ls(Object[]) [");
        for (Object i : args)
        {
            System.out.print(i + " ");
        }
        System.out.println("]");
    }

    public void testProgram()
    {
        List<List<List<Token>>> x = new Parser("abc def|ghi jkl;mno pqr|stu vwx").program();
        assertEquals("abc", x.get(0).get(0).get(0).toString());
        assertEquals("def", x.get(0).get(0).get(1).toString());
        assertEquals("ghi", x.get(0).get(1).get(0).toString());
        assertEquals("jkl", x.get(0).get(1).get(1).toString());
        assertEquals("mno", x.get(1).get(0).get(0).toString());
        assertEquals("pqr", x.get(1).get(0).get(1).toString());
        assertEquals("stu", x.get(1).get(1).get(0).toString());
        assertEquals("vwx", x.get(1).get(1).get(1).toString());
    }

    public void testStatements()
    {
        List<List<Token>> x = new Parser("abc def|ghi jkl|mno pqr").program().get(0);
        assertEquals("abc", x.get(0).get(0).toString());
        assertEquals("def", x.get(0).get(1).toString());
        assertEquals("ghi", x.get(1).get(0).toString());
        assertEquals("jkl", x.get(1).get(1).toString());
        assertEquals("mno", x.get(2).get(0).toString());
        assertEquals("pqr", x.get(2).get(1).toString());
    }

    public void testSimpleValue()
    {
        List<Token> x = new Parser(
            "abc def.ghi http://www.osgi.org?abc=&x=1 [1,2,3] {{{{{{{xyz}}}}}}} (immediate) {'{{{{{'} {\\{} 'abc{}'").program().get(0).get(0);
        assertEquals("abc", x.get(0).toString());
        assertEquals("def.ghi", x.get(1).toString());
        assertEquals("http://www.osgi.org?abc=&x=1", x.get(2).toString());
        assertEquals("1,2,3", x.get(3).toString());
        assertEquals("{{{{{{xyz}}}}}}", x.get(4).toString());
        assertEquals("immediate", x.get(5).toString());
        assertEquals("'{{{{{'", x.get(6).toString());
        assertEquals("\\{", x.get(7).toString());
        assertEquals("'abc{}'", x.get(8).toString());
    }

    void each(CommandSession session, Collection<Object> list, Function closure)
        throws Exception
    {
        List<Object> args = new ArrayList<Object>();
        args.add(null);
        for (Object x : list)
        {
            args.set(0, x);
            closure.execute(session, args);
        }
    }

}
