package com.contentmover.mobile;
/** Shared keypad state for initial setup, confirmation and ordinary login. */
public final class PinEntry {
  private String digits="",first=null;private final boolean setup;
  public PinEntry(boolean setup){this.setup=setup;}
  public int length(){return digits.length();}
  public boolean confirming(){return setup&&first!=null;}
  public void clear(){digits="";}
  public void restart(){digits="";first=null;}
  public void delete(){if(!digits.isEmpty())digits=digits.substring(0,digits.length()-1);}
  public String digit(int number){
    if(number<0||number>9||digits.length()==4)throw new IllegalArgumentException();digits+=number;
    if(digits.length()!=4)return null;
    if(setup&&first==null){first=digits;digits="";return null;}
    String result=digits;digits="";
    if(setup&&!first.equals(result)){first=null;throw new IllegalArgumentException("两次密码不一致，请重新设置");}
    return result;
  }
}
