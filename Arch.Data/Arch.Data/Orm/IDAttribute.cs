﻿using System;

namespace Arch.Data.Orm
{
    [AttributeUsage(AttributeTargets.Field | AttributeTargets.Property, Inherited = false, AllowMultiple = false)]
    public class IDAttribute : Attribute
    {
        public IDAttribute() : base() { }
    }
}
